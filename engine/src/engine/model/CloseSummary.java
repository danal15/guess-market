package engine.model;

/** What happened when an event was closed, so the interface can report it. */
public final class CloseSummary {

    private final String winningOptionName;
    private int winnersPaid;
    private double totalPaidOut;
    private double commissionCollected;
    private double returnedToMarketMaker;
    private int cancelledOrders;

    public CloseSummary(String winningOptionName) {
        this.winningOptionName = winningOptionName;
    }

    public void recordPayout(double amount, double commission) {
        winnersPaid++;
        totalPaidOut += amount;
        commissionCollected += commission;
    }

    public void recordReturnedToMarketMaker(double amount) {
        returnedToMarketMaker += amount;
    }

    public void recordCancelledOrders(int count) {
        cancelledOrders += count;
    }

    public String getWinningOptionName() {
        return winningOptionName;
    }

    public int getWinnersPaid() {
        return winnersPaid;
    }

    public double getTotalPaidOut() {
        return totalPaidOut;
    }

    public double getCommissionCollected() {
        return commissionCollected;
    }

    public double getReturnedToMarketMaker() {
        return returnedToMarketMaker;
    }

    public int getCancelledOrders() {
        return cancelledOrders;
    }
}
