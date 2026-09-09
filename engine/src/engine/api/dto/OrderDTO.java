package engine.api.dto;

public final class OrderDTO {

    private final String userName;
    private final String sideLabel;
    private final long quantity;
    private final double price;

    public OrderDTO(String userName, String sideLabel, long quantity, double price) {
        this.userName = userName;
        this.sideLabel = sideLabel;
        this.quantity = quantity;
        this.price = price;
    }

    public String getUserName() {
        return userName;
    }

    public String getSideLabel() {
        return sideLabel;
    }

    public long getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }
}
