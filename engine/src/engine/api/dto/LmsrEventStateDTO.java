package engine.api.dto;

import java.util.List;

public final class LmsrEventStateDTO {

    private final EventDTO event;
    private final OptionStateDTO option1State;
    private final OptionStateDTO option2State;
    private final double accountBalance;
    private final double totalCommissionCollected;
    private final List<TradeDTO> tradesNewestFirst;
    private final String winningOptionName;
    private final List<PricePointDTO> priceHistory;

    public LmsrEventStateDTO(EventDTO event, OptionStateDTO option1State, OptionStateDTO option2State,
                             double accountBalance, double totalCommissionCollected,
                             List<TradeDTO> tradesNewestFirst, String winningOptionName,
                             List<PricePointDTO> priceHistory) {
        this.event = event;
        this.option1State = option1State;
        this.option2State = option2State;
        this.accountBalance = accountBalance;
        this.totalCommissionCollected = totalCommissionCollected;
        this.tradesNewestFirst = tradesNewestFirst;
        this.winningOptionName = winningOptionName;
        this.priceHistory = priceHistory;
    }

    public EventDTO getEvent() {
        return event;
    }

    public OptionStateDTO getOption1State() {
        return option1State;
    }

    public OptionStateDTO getOption2State() {
        return option2State;
    }

    public double getAccountBalance() {
        return accountBalance;
    }

    public double getTotalCommissionCollected() {
        return totalCommissionCollected;
    }

    public List<TradeDTO> getTradesNewestFirst() {
        return tradesNewestFirst;
    }

    /** Null while the event is still open. */
    public String getWinningOptionName() {
        return winningOptionName;
    }

    public List<PricePointDTO> getPriceHistory() {
        return priceHistory;
    }
}
