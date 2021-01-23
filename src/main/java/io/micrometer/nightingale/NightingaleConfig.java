package io.micrometer.nightingale;

import io.micrometer.core.instrument.config.validate.PropertyValidator;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link NightingaleMeterRegistry}.
 *
 * @author ning.chai@foxmail.com
 * @since 1.0.0
 */
public interface NightingaleConfig extends StepRegistryConfig {
    /**
     * Accept configuration defaults
     */
    NightingaleConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "nightingale";
    }

    /**
     * @return Global tag
     */
    default String tags(){
        return getString(this,"tags").orElse("tag");
    }

    /**
     * @return The URI for the n9e of agent or transfer. The default is {@code http://localhost:5810/v1/push}.
     */
    @Nullable
    default String uri() {
        return getUrlString(this, "url").orElse("http://localhost:5810/v1/push");
    }


    @Override
    default Duration step() {
        return (Duration) PropertyValidator.getDuration(this, "step").orElse(Duration.ofSeconds(10));
    }

    default String counterType(){
        return getString(this,"counterType").orElse("GAUGE");
    }


    default String endpoint(){
        InetAddress addr = null;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return getString(this,"endpoint").orElse(addr.toString());
    }

    default String nid() {
        return getString(this, "nid").orElse("");
    }


    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("endpoint", NightingaleConfig::endpoint),
                checkRequired("uri", NightingaleConfig::uri)
        );
    }
}
