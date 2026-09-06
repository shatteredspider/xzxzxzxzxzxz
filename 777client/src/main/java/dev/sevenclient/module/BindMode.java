package dev.sevenclient.module;

/** Standard toggle, or active-only-while-held. */
public enum BindMode {
    TOGGLE("Toggle"),
    HOLD("Hold");

    private final String label;

    BindMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
