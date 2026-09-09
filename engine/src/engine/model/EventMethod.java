package engine.model;

public enum EventMethod {
    LMSR("LMSR"),
    ORDER_BOOK("Order Book");

    private final String label;

    EventMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
