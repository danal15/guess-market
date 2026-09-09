package engine.api.dto;

import java.util.List;

public final class OrderBookEventStateDTO {

    private final EventDTO event;
    private final int baseValue;
    private final boolean allowMint;
    private final double accountBalance;
    private final double totalCommissionCollected;
    private final OrderBookSideDTO option1Book;
    private final OrderBookSideDTO option2Book;
    private final List<ParticipantDTO> participants;
    private final String winningOptionName;
    private final List<PricePointDTO> priceHistory;

    public OrderBookEventStateDTO(EventDTO event, int baseValue, boolean allowMint,
                                  double accountBalance, double totalCommissionCollected,
                                  OrderBookSideDTO option1Book, OrderBookSideDTO option2Book,
                                  List<ParticipantDTO> participants, String winningOptionName,
                                  List<PricePointDTO> priceHistory) {
        this.event = event;
        this.baseValue = baseValue;
        this.allowMint = allowMint;
        this.accountBalance = accountBalance;
        this.totalCommissionCollected = totalCommissionCollected;
        this.option1Book = option1Book;
        this.option2Book = option2Book;
        this.participants = participants;
        this.winningOptionName = winningOptionName;
        this.priceHistory = priceHistory;
    }

    public EventDTO getEvent() {
        return event;
    }

    public int getBaseValue() {
        return baseValue;
    }

    public boolean isAllowMint() {
        return allowMint;
    }

    public double getAccountBalance() {
        return accountBalance;
    }

    public double getTotalCommissionCollected() {
        return totalCommissionCollected;
    }

    public OrderBookSideDTO getOption1Book() {
        return option1Book;
    }

    public OrderBookSideDTO getOption2Book() {
        return option2Book;
    }

    public List<ParticipantDTO> getParticipants() {
        return participants;
    }

    /** Null while the event is still open. */
    public String getWinningOptionName() {
        return winningOptionName;
    }

    public List<PricePointDTO> getPriceHistory() {
        return priceHistory;
    }
}
