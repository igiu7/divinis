package com.divinis.platform.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.divinis.Divinis;

/**
 * A small config (the same calls as NeoForge's ModConfigSpec, on every loader): values defined with a Builder, kept
 * in a TOML file - "[section]" headers, "key = value" lines and "#" comments. load() reads the file, keeps what it
 * finds that is valid, and writes it back complete with every key and its comment.
 */
public final class ModConfigSpec {
    private final List<ConfigValue<?>> values;

    private ModConfigSpec(List<ConfigValue<?>> values) {
        this.values = values;
    }

    public void load(Path file) {
        Map<String, String> raw = new LinkedHashMap<>();
        if (Files.exists(file)) {
            try {
                String section = "";
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) continue;
                    if (t.startsWith("[") && t.endsWith("]")) {
                        section = t.substring(1, t.length() - 1).trim() + ".";
                        continue;
                    }
                    int eq = t.indexOf('=');
                    if (eq > 0) raw.put(section + t.substring(0, eq).trim(), t.substring(eq + 1).trim());
                }
            } catch (IOException e) {
                Divinis.LOGGER.error("Could not read {}", file, e);
            }
        }
        for (ConfigValue<?> v : values) {
            String s = raw.get(v.path);
            if (s != null && !v.parse(s)) {
                Divinis.LOGGER.warn("Config {}: {} is not valid, using {}", file.getFileName(), v.path, v.write());
            }
        }
        save(file);
    }

    private void save(Path file) {
        StringBuilder sb = new StringBuilder();
        String section = null;
        for (ConfigValue<?> v : values) {
            int dot = v.path.lastIndexOf('.');
            String sec = dot < 0 ? "" : v.path.substring(0, dot);
            if (!sec.equals(section)) {
                if (section != null) sb.append('\n');
                if (!sec.isEmpty()) sb.append('[').append(sec).append("]\n");
                section = sec;
            }
            String indent = sec.isEmpty() ? "" : "\t";
            for (String c : v.comment) sb.append(indent).append('#').append(c).append('\n');
            if (v.range != null) sb.append(indent).append("#Range: ").append(v.range).append('\n');
            sb.append(indent).append(v.path.substring(dot + 1)).append(" = ").append(v.write()).append('\n');
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Divinis.LOGGER.error("Could not write {}", file, e);
        }
    }

    public static final class Builder {
        private final List<ConfigValue<?>> values = new ArrayList<>();
        private List<String> comment = new ArrayList<>();

        public Builder comment(String... lines) {
            for (String l : lines) comment.add(l);
            return this;
        }

        private <V extends ConfigValue<?>> V add(V v) {
            v.comment = comment;
            comment = new ArrayList<>();
            values.add(v);
            return v;
        }

        public IntValue defineInRange(String path, int def, int min, int max) {
            IntValue v = add(new IntValue(path, def, min, max));
            v.range = min + " ~ " + max;
            return v;
        }

        public DoubleValue defineInRange(String path, double def, double min, double max) {
            DoubleValue v = add(new DoubleValue(path, def, min, max));
            v.range = min + " ~ " + max;
            return v;
        }

        public BooleanValue define(String path, boolean def) {
            return add(new BooleanValue(path, def));
        }

        public ConfigValue<List<? extends String>> defineListAllowEmpty(String path, List<? extends String> def,
                                                                       Supplier<String> newElement, Predicate<Object> valid) {
            return add(new StringListValue(path, def, valid));
        }

        public ModConfigSpec build() {
            return new ModConfigSpec(List.copyOf(values));
        }
    }

    public abstract static class ConfigValue<T> implements Supplier<T> {
        final String path;
        List<String> comment = List.of();
        String range;
        T value;

        ConfigValue(String path, T def) {
            this.path = path;
            this.value = def;
        }

        @Override
        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }

        abstract boolean parse(String s);

        abstract String write();
    }

    public static final class IntValue extends ConfigValue<Integer> {
        private final int min, max;

        IntValue(String path, int def, int min, int max) {
            super(path, def);
            this.min = min;
            this.max = max;
        }

        @Override
        boolean parse(String s) {
            try {
                int v = Integer.parseInt(s);
                if (v < min || v > max) return false;
                value = v;
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        String write() {
            return String.valueOf(value);
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {
        private final double min, max;

        DoubleValue(String path, double def, double min, double max) {
            super(path, def);
            this.min = min;
            this.max = max;
        }

        @Override
        boolean parse(String s) {
            try {
                double v = Double.parseDouble(s);
                if (v < min || v > max) return false;
                value = v;
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        String write() {
            return String.valueOf(value);
        }
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(String path, boolean def) {
            super(path, def);
        }

        @Override
        boolean parse(String s) {
            if (!s.equals("true") && !s.equals("false")) return false;
            value = Boolean.parseBoolean(s);
            return true;
        }

        @Override
        String write() {
            return String.valueOf(value);
        }
    }

    public static final class StringListValue extends ConfigValue<List<? extends String>> {
        private final Predicate<Object> valid;

        StringListValue(String path, List<? extends String> def, Predicate<Object> valid) {
            super(path, def);
            this.valid = valid;
        }

        @Override
        boolean parse(String s) {
            if (!s.startsWith("[") || !s.endsWith("]")) return false;
            List<String> out = new ArrayList<>();
            String body = s.substring(1, s.length() - 1).trim();
            if (!body.isEmpty()) {
                for (String part : body.split(",")) {
                    String p = part.trim();
                    if (p.length() < 2 || !p.startsWith("\"") || !p.endsWith("\"")) return false;
                    String e = p.substring(1, p.length() - 1);
                    if (!valid.test(e)) return false;
                    out.add(e);
                }
            }
            value = List.copyOf(out);
            return true;
        }

        @Override
        String write() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < value.size(); i++) sb.append(i > 0 ? ", " : "").append('"').append(value.get(i)).append('"');
            return sb.append(']').toString();
        }
    }
}
