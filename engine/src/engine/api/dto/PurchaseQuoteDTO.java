package engine.api.dto;

/** What a purchase would cost, worked out before anything is bought. */
public final class PurchaseQuoteDTO {

    private final double sharesCost;
    private final double commission;
    private final double totalCost;
    private final double averagePricePerShare;
    private final double priceAfterwards;
    private final double buyerBalance;
    private final boolean affordable;

    public PurchaseQuoteDTO(double sharesCost, double commission, double totalCost,
                            double averagePricePerShare, double priceAfterwards,
                            double buyerBalance, boolean affordable) {
        this.sharesCost = sharesCost;
        this.commission = commission;
        this.totalCost = totalCost;
        this.averagePricePerShare = averagePricePerShare;
        this.priceAfterwards = priceAfterwards;
        this.buyerBalance = buyerBalance;
        this.affordable = affordable;
    }

    public double getSharesCost() {
        return sharesCost;
    }

    public double getCommission() {
        return commission;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public double getAveragePricePerShare() {
        return averagePricePerShare;
    }

    public double getPriceAfterwards() {
        return priceAfterwards;
    }

    public double getBuyerBalance() {
        return buyerBalance;
    }

    public boolean isAffordable() {
        return affordable;
    }
}
