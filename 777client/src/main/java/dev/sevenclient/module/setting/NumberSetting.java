package dev.sevenclient.module.setting;

public class NumberSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, double def, double min, double max, double step) {
        super(name, def);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }

    public double val() { return value; }
    public float valf() { return value.floatValue(); }
    public int vali() { return (int) Math.round(value); }

    @Override
    public void set(Double v) {
        double clamped = Math.max(min, Math.min(max, v));
        // snap to step so slider values stay clean
        double snapped = Math.round(clamped / step) * step;
        this.value = Math.max(min, Math.min(max, snapped));
    }

    /** 0..1 position for slider rendering. */
    public double normalized() {
        return (value - min) / (max - min);
    }

    public void setNormalized(double t) {
        set(min + (max - min) * Math.max(0.0, Math.min(1.0, t)));
    }

    @Override
    public String serialize() {
        return Double.toString(value);
    }

    @Override
    public void deserialize(String raw) {
        try { set(Double.parseDouble(raw)); } catch (NumberFormatException ignored) { }
    }
}
