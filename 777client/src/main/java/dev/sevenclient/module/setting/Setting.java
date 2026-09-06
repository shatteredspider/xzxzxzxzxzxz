package dev.sevenclient.module.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {

    protected final String name;
    protected T value;
    private BooleanSupplier visibility = () -> true;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public T get() {
        return value;
    }

    public void set(T v) {
        this.value = v;
    }

    /** Hide this setting in the GUI unless the predicate passes. */
    public Setting<T> visibleWhen(BooleanSupplier predicate) {
        this.visibility = predicate;
        return this;
    }

    public boolean visible() {
        return visibility.getAsBoolean();
    }

    public abstract String serialize();

    public abstract void deserialize(String raw);
}
