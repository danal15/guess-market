package engine.core.ob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What actually happened when an order was submitted. */
public class OrderOutcome {

    public static final class Fill {
        private final long quantity;
        private final double price;
        private final String counterpartyName;
        private final String kindLabel;

        public Fill(long quantity, double price, String counterpartyName, String kindLabel) {
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

        /** Null when the shares were minted rather than bought from someone. */
        public String getCounterpartyName() {
            return counterpartyName;
        }

        public String getKindLabel() {
            return kindLabel;
        }
    }

    private final List<Fill> fills = new ArrayList<>();
    private long mintedQuantity;
    private long restingQuantity;
    private double cashPaid;
    private double cashReceived;
    private double commissionPaid;
    private boolean traderBlocked;

    public void addFill(Fill fill) {
        fills.add(fill);
    }

    public void addMinted(long quantity) {
        mintedQuantity += quantity;
    }

    public void addCashPaid(double amount) {
        cashPaid += amount;
    }

    public void addCashReceived(double amount) {
        cashReceived += amount;
    }

    public void addCommissionPaid(double amount) {
        commissionPaid += amount;
    }

    public void setRestingQuantity(long quantity) {
        this.restingQuantity = quantity;
    }

    public void setTraderBlocked(boolean blocked) {
        this.traderBlocked = blocked;
    }

    public List<Fill> getFills() {
        return Collections.unmodifiableList(fills);
    }

    public long getFilledQuantity() {
        long total = 0;
        for (Fill fill : fills) {
            total += fill.getQuantity();
        }
        return total;
    }

    public long getMintedQuantity() {
        return mintedQuantity;
    }

    public long getRestingQuantity() {
        return restingQuantity;
    }

    public double getCashPaid() {
        return cashPaid;
    }

    public double getCashReceived() {
        return cashReceived;
    }

    public double getCommissionPaid() {
        return commissionPaid;
    }

    public boolean isTraderBlocked() {
        return traderBlocked;
    }
}
