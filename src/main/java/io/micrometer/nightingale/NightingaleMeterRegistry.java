package io.micrometer.nightingale;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
/**
 * {@link MeterRegistry} for Nightingale.
 *
 * @author ning.chai@foxmail.com
 * @since 1.0.0
 */
public class NightingaleMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("n9e-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(NightingaleMeterRegistry.class);
    private final NightingaleConfig config;
    private final HttpSender httpClient;

    @SuppressWarnings("deprecation")
    public NightingaleMeterRegistry(NightingaleConfig config, Clock clock) {

        this(config, clock, DEFAULT_THREAD_FACTORY, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private NightingaleMeterRegistry(NightingaleConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);

        config().namingConvention(new NightingaleNamingConvention());

        this.config = config;
        this.httpClient = httpClient;

        start(threadFactory);
    }

    public static Builder builder(NightingaleConfig config) {
        return new Builder(config);
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            try {
                httpClient.post(config.uri())
                        .withJsonContent(
                                batch.stream().flatMap(m -> m.match(
                                        this::writeGauge,
                                        this::writeCounter,
                                        this::writeTimer,
                                        this::writeSummary,
                                        this::writeLongTaskTimer,
                                        this::writeTimeGauge,
                                        this::writeFunctionCounter,
                                        this::writeFunctionTimer,
                                        this::writeCustomMetric)
                                ).collect(Collectors.joining(",", "[", "]"))
                        )
                        .send()
                        .onSuccess(response -> logger.debug("successfully sent {} metrics to n9e.", batch.size()))
                        .onError(response -> logger.error("failed to send metrics to n9e: {}", response.body()));
            } catch (Throwable t) {
                logger.warn("failed to send metrics to n9e", t);
            }
        }
    }

    Stream<String> writeSummary(DistributionSummary summary) {
        long wallTime = config().clock().wallTime();
        return Stream.of(
                writeMetric(idWithSuffix(summary.getId(), "count"), wallTime, summary.count()),
                writeMetric(idWithSuffix(summary.getId(), "avg"), wallTime, summary.mean()),
                writeMetric(idWithSuffix(summary.getId(), "sum"), wallTime, summary.totalAmount()),
                writeMetric(idWithSuffix(summary.getId(), "max"), wallTime, summary.max())
        );
    }

    Stream<String> writeFunctionTimer(FunctionTimer timer) {
        long wallTime = config().clock().wallTime();
        return Stream.of(
                writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
                writeMetric(idWithSuffix(timer.getId(), "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                writeMetric(idWithSuffix(timer.getId(), "sum"), wallTime, timer.totalTime(getBaseTimeUnit()))
        );
    }

    Stream<String> writeTimer(Timer timer) {
        long wallTime = config().clock().wallTime();
        return Stream.of(
                writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
                writeMetric(idWithSuffix(timer.getId(), "max"), wallTime, timer.max(getBaseTimeUnit())),
                writeMetric(idWithSuffix(timer.getId(), "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                writeMetric(idWithSuffix(timer.getId(), "sum"), wallTime, timer.totalTime(getBaseTimeUnit()))
        );
    }

    // VisibleForTesting
    Stream<String> writeFunctionCounter(FunctionCounter counter) {
        double count = counter.count();
        if (Double.isFinite(count)) {
            return Stream.of(writeMetric(counter.getId(), config().clock().wallTime(), count));
        }
        return Stream.empty();
    }

    Stream<String> writeCounter(Counter counter) {
        return Stream.of(writeMetric(counter.getId(), config().clock().wallTime(), counter.count()));
    }

    // VisibleForTesting
    Stream<String> writeGauge(Gauge gauge) {
        double value = gauge.value();
        if (Double.isFinite(value)) {
            return Stream.of(writeMetric(gauge.getId(), config().clock().wallTime(), value));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeTimeGauge(TimeGauge timeGauge) {
        double value = timeGauge.value(getBaseTimeUnit());
        if (Double.isFinite(value)) {
            return Stream.of(writeMetric(timeGauge.getId(), config().clock().wallTime(), value));
        }
        return Stream.empty();
    }

    Stream<String> writeLongTaskTimer(LongTaskTimer timer) {
        long wallTime = config().clock().wallTime();
        return Stream.of(
                writeMetric(idWithSuffix(timer.getId(), "activeTasks"), wallTime, timer.activeTasks()),
                writeMetric(idWithSuffix(timer.getId(), "duration"), wallTime, timer.duration(getBaseTimeUnit()))
        );
    }

    protected Long generateTimestamp() {
        return config().clock().wallTime() / 1000;
    }

    // VisibleForTesting
    Stream<String> writeCustomMetric(Meter meter) {
        List<Tag> tags = getConventionTags(meter.getId());
        List<String> metrics = new ArrayList<>();
        for (Measurement measurement : meter.measure()) {
            double value = measurement.getValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            metrics.add(new N9eMetricBuilder()
                    .field("metric", measurement.getStatistic().getTagValueRepresentation().replace("_","."))
                    .timestamp(generateTimestamp())
                    .value(value)
                    .step(config)
                    .endpoint(config.endpoint())
                    .tags(tags)
                    .build());
        }
        return metrics.stream();
    }

    String writeMetric(Meter.Id id, long wallTime, double value) {
        return new N9eMetricBuilder()
                .field("metric", getConventionName(id).replace("_","."))
                .timestamp(wallTime/1000)
                .value(value)
                .step(config)
                .endpoint(config.endpoint())
                .counterType(config.counterType())
                .build();
    }

    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private static class N9eMetricBuilder {
        private final StringBuilder sb = new StringBuilder("{");

        N9eMetricBuilder field(String key, String value) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('\"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('\"');
            return this;
        }

        N9eMetricBuilder timestamp(long wallTime) {
            sb.append(",\"timestamp\":").append(wallTime);
            return this;
        }

        N9eMetricBuilder value(double value) {
            sb.append(",\"value\":").append(value);
            return this;
        }

        N9eMetricBuilder endpoint(String endpoint) {
            sb.append(",\"endpoint\":").append(endpoint);
            return this;
        }

        N9eMetricBuilder nid(String nid) {
            sb.append(",\"nid\":").append(nid);
            return this;
        }

        N9eMetricBuilder counterType(String nid) {
            sb.append(",\"counterType\":").append(nid);
            return this;
        }

        N9eMetricBuilder step(NightingaleConfig step) {
            sb.append(",\"step\":").append(step.step().getSeconds());
            return this;
        }

        N9eMetricBuilder tags(List<Tag> tags) {
            N9eMetricBuilder tagBuilder = new N9eMetricBuilder();
            if (tags.isEmpty()) {
                // tags field is required for n9e, use hostname as a default tag
                try {
                    tagBuilder.field("hostname", InetAddress.getLocalHost().getHostName());
                } catch (UnknownHostException ignore) {
                    /* ignore */
                }
            } else {
                for (Tag tag : tags) {
                    tagBuilder.field(tag.getKey(), tag.getValue());
                }
            }

            sb.append(",\"tags\":").append(tagBuilder.build());
            return this;
        }

        String build() {
            return sb.append('}').toString();
        }
    }

    public static class Builder {
        private final NightingaleConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(NightingaleConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public NightingaleMeterRegistry build() {
            return new NightingaleMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}
