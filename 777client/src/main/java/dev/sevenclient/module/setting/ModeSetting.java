package dev.sevenclient.module.setting;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting<String> {

    private final List<String> options;

    public ModeSetting(String name, String def, String... options) {
        super(name, def);
        this.options = Arrays.asList(options);
    }

    public List<String> options() {
        return options;
    }

    public boolean is(String mode) {
        return value.equalsIgnoreCase(mode);
    }

    public int index() {
        int i = options.indexOf(value);
        return i < 0 ? 0 : i;
    }

    public void cycle(int dir) {
        int i = (index() + dir) % options.size();
        if (i < 0) i += options.size();
        value = options.get(i);
    }

    @Override
    public String serialize() {
        return value;
    }

    @Override
    public void deserialize(String raw) {
        if (options.contains(raw)) value = raw;
    }
}
