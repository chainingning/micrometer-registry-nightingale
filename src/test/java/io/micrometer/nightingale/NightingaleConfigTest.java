package io.micrometer.nightingale;

import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NightingaleConfigTest {
    private final Map<String, String> props = new HashMap<>();
    private final NightingaleConfig config = props::get;

    @Test
    void invalid() {
        props.put("nightingale.uri", "bad");

        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
                .containsExactly("must be a valid URL");
    }

    @Test
    void valid() {
        assertThat(config.validate().isValid()).isTrue();
    }
}