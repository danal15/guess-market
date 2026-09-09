package engine.api.dto;

import java.util.List;

/**
 * How one user is involved in one event. For an LMSR event the trade rows are
 * filled in; for an order book event the holdings and the amounts paid are.
 */
public final class UserEventInvolvementDTO {

    private final int eventId;
    private final String eventName;
    private final String methodLabel;
    private final boolean orderBook;
    private final String statusLabel;
    private final boolean marketMaker;
    private final double commissionPaid;

    private final List<TradeDTO> tradesNewestFirst;

    private final String option1Name;
    private final long option1Quantity;
    private final double option1Paid;
    private final String option2Name;
    private final long option2Quantity;
    private final double option2Paid;

    private final String winningOptionName;
    private final Double profitOrLoss;
    private final Double tradingResult;
    private final int openOrderCount;
    private final long openOrderQuantity;
    private final double marketMakerPaid;
    private final double marketMakerReceived;

    public UserEventInvolvementDTO(int eventId, String eventName, String methodLabel, boolean orderBook,
                                   String statusLabel, boolean marketMaker, double commissionPaid,
                                   List<TradeDTO> tradesNewestFirst,
                                   String option1Name, long option1Quantity, double option1Paid,
                                   String option2Name, long option2Quantity, double option2Paid,
                                   String winningOptionName, Double profitOrLoss,
                                   Double tradingResult, double marketMakerPaid,
                                   double marketMakerReceived,
                                   int openOrderCount, long openOrderQuantity) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.methodLabel = methodLabel;
        this.orderBook = orderBook;
        this.statusLabel = statusLabel;
        this.marketMaker = marketMaker;
        this.commissionPaid = commissionPaid;
        this.tradesNewestFirst = tradesNewestFirst;
        this.option1Name = option1Name;
        this.option1Quantity = option1Quantity;
        this.option1Paid = option1Paid;
        this.option2Name = option2Name;
        this.option2Quantity = option2Quantity;
        this.option2Paid = option2Paid;
        this.winningOptionName = winningOptionName;
        this.profitOrLoss = profitOrLoss;
        this.tradingResult = tradingResult;
        this.marketMakerPaid = marketMakerPaid;
        this.marketMakerReceived = marketMakerReceived;
        this.openOrderCount = openOrderCount;
        this.openOrderQuantity = openOrderQuantity;
    }

    public int getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public String getMethodLabel() {
        return methodLabel;
    }

    public boolean isOrderBook() {
        return orderBook;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public boolean isMarketMaker() {
        return marketMaker;
    }

    public double getCommissionPaid() {
        return commissionPaid;
    }

    public List<TradeDTO> getTradesNewestFirst() {
        return tradesNewestFirst;
    }

    public String getOption1Name() {
        return option1Name;
    }

    public long getOption1Quantity() {
        return option1Quantity;
    }

    public double getOption1Paid() {
        return option1Paid;
    }

    public String getOption2Name() {
        return option2Name;
    }

    public long getOption2Quantity() {
        return option2Quantity;
    }

    public double getOption2Paid() {
        return option2Paid;
    }

    /** Null while the event is still open. */
    public String getWinningOptionName() {
        return winningOptionName;
    }

    /** The whole effect on the balance. Null while the event is still open, and
     *  null for a user who never held a position in it. */
    public Double getProfitOrLoss() {
        return profitOrLoss;
    }

    /** Just the buying and selling part. Null on the same terms as the result. */
    public Double getTradingResult() {
        return tradingResult;
    }

    /** Money put in because this user runs the event (the LMSR subsidy). */
    public double getMarketMakerPaid() {
        return marketMakerPaid;
    }

    /** Orders of this user still waiting in the book, not yet traded. */
    public int getOpenOrderCount() {
        return openOrderCount;
    }

    public long getOpenOrderQuantity() {
        return openOrderQuantity;
    }

    /** Commission and leftover funds taken back as the event's market maker. */
    public double getMarketMakerReceived() {
        return marketMakerReceived;
    }
}
