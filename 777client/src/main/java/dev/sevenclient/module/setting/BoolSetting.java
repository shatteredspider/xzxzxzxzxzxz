package dev.sevenclient.module.setting;

public class BoolSetting extends Setting<Boolean> {

    public BoolSetting(String name, boolean def) {
        super(name, def);
    }

    public boolean is() {
        return value;
    }

    public void toggle() {
        value = !value;
    }

    @Override
    public String serialize() {
        return Boolean.toString(value);
    }

    @Override
    public void deserialize(String raw) {
        value = Boolean.parseBoolean(raw);
    }
}
