package engine.model;

import java.io.Serializable;

/**
 * One of the two possible answers of an event.
 * The counter holds the total number of shares of this option currently in
 * existence: for LMSR that is simply everything ever bought, for an order book
 * it grows on the initial allocation and on every mint, and a resale between
 * two users does not change it.
 */
public class Option implements Serializable {

    private static final long serialVersionUID = 2L;

    private final String name;
    private long sharesOutstanding;

    public Option(String name) {
        this.name = name;
        this.sharesOutstanding = 0L;
    }

    void addShares(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to add must be positive: " + quantity);
        }
        sharesOutstanding += quantity;
    }

    public String getName() {
        return name;
    }

    public long getSharesOutstanding() {
        return sharesOutstanding;
    }
}
