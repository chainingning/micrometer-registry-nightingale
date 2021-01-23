package io.micrometer.nightingale;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Anton ning.chai@foxmail.com
 */
class NightingaleNamingConventionTest {
    private NightingaleNamingConvention convention = new NightingaleNamingConvention();

    @Test
    void defaultToSnakeCase() {
        assertThat(convention.name("gauge.size", Meter.Type.GAUGE)).isEqualTo("gauge_size");
    }
}