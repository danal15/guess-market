package engine.model;

public enum OrderSide {
    BUY("Buy"),
    SELL("Sell");

    private final String label;

    OrderSide(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
