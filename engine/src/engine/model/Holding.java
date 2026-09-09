package engine.model;

import java.io.Serializable;

public class Holding implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int eventId;
    private final long[] quantities = new long[2];
    private final double[] amountPaid = new double[2];
    private final double[] amountReceived = new double[2];
    private double commissionPaid;
    private boolean hasEverOrdered;

    public Holding(int eventId) {
        this.eventId = eventId;
    }

    public void addShares(int optionIndex, long quantity, double cashPaid) {
        checkIndex(optionIndex);
        quantities[optionIndex] += quantity;
        amountPaid[optionIndex] += cashPaid;
    }

    public void removeShares(int optionIndex, long quantity, double cashReceived) {
        checkIndex(optionIndex);
        if (quantity > quantities[optionIndex]) {
            throw new IllegalArgumentException("Cannot remove more shares than held.");
        }
        quantities[optionIndex] -= quantity;
        amountReceived[optionIndex] += cashReceived;
    }

    public void addCommission(double amount) {
        commissionPaid += amount;
    }

    /** Records money received at resolution without changing the held quantity. */
    public void recordPayout(int optionIndex, double amount) {
        checkIndex(optionIndex);
        amountReceived[optionIndex] += amount;
    }

    public void markOrdered() {
        hasEverOrdered = true;
    }

    public int getEventId() {
        return eventId;
    }

    public long getQuantity(int optionIndex) {
        checkIndex(optionIndex);
        return quantities[optionIndex];
    }

    public double getAmountPaid(int optionIndex) {
        checkIndex(optionIndex);
        return amountPaid[optionIndex];
    }

    public double getAmountReceived(int optionIndex) {
        checkIndex(optionIndex);
        return amountReceived[optionIndex];
    }

    public double getCommissionPaid() {
        return commissionPaid;
    }

    public boolean hasEverOrdered() {
        return hasEverOrdered;
    }

    public boolean holdsAnything() {
        return quantities[0] > 0 || quantities[1] > 0;
    }

    /** Net cash result of this participation, given the payout already received. */
    public double netCashFlow() {
        double paid = amountPaid[0] + amountPaid[1] + commissionPaid;
        double received = amountReceived[0] + amountReceived[1];
        return received - paid;
    }

    private void checkIndex(int optionIndex) {
        if (optionIndex != 0 && optionIndex != 1) {
            throw new IllegalArgumentException("Option index must be 0 or 1, got: " + optionIndex);
        }
    }
}
