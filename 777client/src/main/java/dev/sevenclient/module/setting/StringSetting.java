package dev.sevenclient.module.setting;

public class StringSetting extends Setting<String> {

    private final int maxLength;

    public StringSetting(String name, String def) {
        this(name, def, 64);
    }

    public StringSetting(String name, String def, int maxLength) {
        super(name, def);
        this.maxLength = maxLength;
    }

    public int maxLength() {
        return maxLength;
    }

    public void append(char c) {
        if (value.length() < maxLength) value = value + c;
    }

    public void backspace() {
        if (!value.isEmpty()) value = value.substring(0, value.length() - 1);
    }

    @Override
    public String serialize() {
        return value;
    }

    @Override
    public void deserialize(String raw) {
        value = raw == null ? "" : raw;
    }
}
