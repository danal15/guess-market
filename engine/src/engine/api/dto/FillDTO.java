package engine.api.dto;

public final class FillDTO {

    private final long quantity;
    private final double price;
    private final String counterpartyName;
    private final String kindLabel;

    public FillDTO(long quantity, double price, String counterpartyName, String kindLabel) {
        this.quantity = quantity;
        this.price = price;
        this.counterpartyName = counterpartyName;
        this.kindLabel = kindLabel;
    }

    public long getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public String getKindLabel() {
        return kindLabel;
    }
}
