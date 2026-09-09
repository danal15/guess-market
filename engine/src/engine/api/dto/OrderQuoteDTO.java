package engine.api.dto;

/**
 * What an order would cost or bring in, worked out before it is submitted.
 * For a buy the amounts are the most the user could pay, since matching at a
 * better price costs less; for a sell they are the least they could receive.
 */
public final class OrderQuoteDTO {

    private final boolean buying;
    private final double orderValue;
    private final double commission;
    private final double totalCost;
    private final double balance;
    private final long sharesHeld;
    private final boolean affordable;
    private final boolean enoughShares;
    private final double maxPrice;
    private final boolean priceValid;

    public OrderQuoteDTO(boolean buying, double orderValue, double commission, double totalCost,
                         double balance, long sharesHeld, boolean affordable, boolean enoughShares,
                         double maxPrice, boolean priceValid) {
        this.buying = buying;
        this.orderValue = orderValue;
        this.commission = commission;
        this.totalCost = totalCost;
        this.balance = balance;
        this.sharesHeld = sharesHeld;
        this.affordable = affordable;
        this.enoughShares = enoughShares;
        this.maxPrice = maxPrice;
        this.priceValid = priceValid;
    }

    public boolean isBuying() {
        return buying;
    }

    public double getOrderValue() {
        return orderValue;
    }

    public double getCommission() {
        return commission;
    }

    /** For a buy: value plus commission. For a sell: what would be received. */
    public double getTotalCost() {
        return totalCost;
    }

    public double getBalance() {
        return balance;
    }

    public long getSharesHeld() {
        return sharesHeld;
    }

    public boolean isAffordable() {
        return affordable;
    }

    public boolean isEnoughShares() {
        return enoughShares;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public boolean isPriceValid() {
        return priceValid;
    }
}
