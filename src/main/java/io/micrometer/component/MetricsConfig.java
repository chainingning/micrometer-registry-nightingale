package io.micrometer.component;

import io.micrometer.core.instrument.Clock;
import io.micrometer.nightingale.NightingaleConfig;
import io.micrometer.nightingale.NightingaleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

	@Bean
	public NightingaleConfig n9eConfig() {
		return NightingaleConfig.DEFAULT;
	}

	@Bean
	public NightingaleMeterRegistry n9eMeterRegistry(NightingaleConfig nightingaleConfig, Clock clock) {
		return new NightingaleMeterRegistry(nightingaleConfig, clock);
	}

}