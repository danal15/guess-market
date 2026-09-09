package engine.model;

import java.io.Serializable;

public final class ExecutedTrade implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        INITIAL_ALLOCATION("Initial allocation"),
        RESALE("Resale"),
        MINT("Mint");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final long sequence;
    private final int optionIndex;
    private final String buyerName;
    private final String sellerName;
    private final long quantity;
    private final double price;
    private final Kind kind;

    public ExecutedTrade(long sequence, int optionIndex, String buyerName, String sellerName,
                         long quantity, double price, Kind kind) {
        this.sequence = sequence;
        this.optionIndex = optionIndex;
        this.buyerName = buyerName;
        this.sellerName = sellerName;
        this.quantity = quantity;
        this.price = price;
        this.kind = kind;
    }

    public long getSequence() {
        return sequence;
    }

    public int getOptionIndex() {
        return optionIndex;
    }

    public String getBuyerName() {
        return buyerName;
    }

    /** Null for a mint or the initial allocation - no counterparty sold anything. */
    public String getSellerName() {
        return sellerName;
    }

    public long getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public Kind getKind() {
        return kind;
    }
}
