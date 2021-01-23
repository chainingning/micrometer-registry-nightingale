package io.micrometer.nightingale;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringEscapeUtils;
import io.micrometer.core.lang.Nullable;

import java.util.regex.Pattern;

/**
 * {@link NamingConvention } for n9e.
 * @author ning.chai@foxmail.com
 */
public class NightingaleNamingConvention implements NamingConvention {

    private static final Pattern BLACKLISTED_CHARS = Pattern.compile("[{}():,=\\[\\]]");

    private final NamingConvention delegate;

    public NightingaleNamingConvention() {
        this(NamingConvention.snakeCase);
    }

    public NightingaleNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    private String format(String name) {
        String normalized = StringEscapeUtils.escapeJson(name);
        return BLACKLISTED_CHARS.matcher(normalized).replaceAll("_");
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return format(delegate.name(name, type, baseUnit));
    }

    @Override
    public String tagKey(String key) {
        return format(delegate.tagKey(key));
    }

    @Override
    public String tagValue(String value) {
        return format(delegate.tagValue(value));
    }
}
