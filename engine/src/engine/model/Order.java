package engine.model;

import java.io.Serializable;

public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long sequence;
    private final String userName;
    private final int optionIndex;
    private final OrderSide side;
    private final double price;
    private final long originalQuantity;
    private long remainingQuantity;

    public Order(long sequence, String userName, int optionIndex, OrderSide side, double price, long quantity) {
        this.sequence = sequence;
        this.userName = userName;
        this.optionIndex = optionIndex;
        this.side = side;
        this.price = price;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
    }

    public void reduce(long quantity) {
        if (quantity > remainingQuantity) {
            throw new IllegalArgumentException("Cannot reduce an order below zero.");
        }
        remainingQuantity -= quantity;
    }

    public boolean isExhausted() {
        return remainingQuantity <= 0;
    }

    public long getSequence() {
        return sequence;
    }

    public String getUserName() {
        return userName;
    }

    public int getOptionIndex() {
        return optionIndex;
    }

    public OrderSide getSide() {
        return side;
    }

    public double getPrice() {
        return price;
    }

    public long getOriginalQuantity() {
        return originalQuantity;
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }
}
