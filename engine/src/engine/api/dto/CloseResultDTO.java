package engine.api.dto;

/** What closing an event actually did, so the user can be told. */
public final class CloseResultDTO {

    private final String eventName;
    private final String winningOptionName;
    private final int winnersPaid;
    private final double totalPaidOut;
    private final double commissionCollected;
    private final double returnedToMarketMaker;
    private final int cancelledOrders;

    public CloseResultDTO(String eventName, String winningOptionName, int winnersPaid,
                          double totalPaidOut, double commissionCollected,
                          double returnedToMarketMaker, int cancelledOrders) {
        this.eventName = eventName;
        this.winningOptionName = winningOptionName;
        this.winnersPaid = winnersPaid;
        this.totalPaidOut = totalPaidOut;
        this.commissionCollected = commissionCollected;
        this.returnedToMarketMaker = returnedToMarketMaker;
        this.cancelledOrders = cancelledOrders;
    }

    public String getEventName() {
        return eventName;
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
