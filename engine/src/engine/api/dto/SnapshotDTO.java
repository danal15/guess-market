package engine.api.dto;

import java.util.List;

/**
 * Everything one user's screen shows, taken in a single pass over the market.
 *
 * The clients poll, and a screen that asked for each piece separately could
 * catch the market mid trade: a balance from before it and holdings from after.
 * Gathering it all under one lock means what a user sees always describes one
 * moment. It also makes the poll a single request rather than half a dozen.
 */
public final class SnapshotDTO {

    private final UserDTO user;
    private final List<UserDTO> users;
    private final List<EventDTO> events;
    private final int totalEventCount;
    private final List<MovementDTO> movements;
    private final List<Double> balanceHistory;
    private final List<Integer> participatingEventIds;

    private final LmsrEventStateDTO lmsrState;
    private final OrderBookEventStateDTO orderBookState;
    private final UserEventInvolvementDTO involvement;

    private final List<ChatMessageDTO> newChatMessages;
    private final int chatTotal;

    public SnapshotDTO(UserDTO user, List<UserDTO> users, List<EventDTO> events, int totalEventCount,
                       List<MovementDTO> movements, List<Double> balanceHistory,
                       List<Integer> participatingEventIds, LmsrEventStateDTO lmsrState,
                       OrderBookEventStateDTO orderBookState, UserEventInvolvementDTO involvement,
                       List<ChatMessageDTO> newChatMessages, int chatTotal) {
        this.user = user;
        this.users = users;
        this.events = events;
        this.totalEventCount = totalEventCount;
        this.movements = movements;
        this.balanceHistory = balanceHistory;
        this.participatingEventIds = participatingEventIds;
        this.lmsrState = lmsrState;
        this.orderBookState = orderBookState;
        this.involvement = involvement;
        this.newChatMessages = newChatMessages;
        this.chatTotal = chatTotal;
    }

    /** The user who asked. */
    public UserDTO getUser() {
        return user;
    }

    /** Everyone logged in, this user included. */
    public List<UserDTO> getUsers() {
        return users;
    }

    /** The events that passed the filter the request carried. */
    public List<EventDTO> getEvents() {
        return events;
    }

    /** How many events exist regardless of the filter, so a screen can say why it is empty. */
    public int getTotalEventCount() {
        return totalEventCount;
    }

    public List<MovementDTO> getMovements() {
        return movements;
    }

    public List<Double> getBalanceHistory() {
        return balanceHistory;
    }

    public List<Integer> getParticipatingEventIds() {
        return participatingEventIds;
    }

    /** The selected event, when it is an LMSR one; null otherwise. */
    public LmsrEventStateDTO getLmsrState() {
        return lmsrState;
    }

    /** The selected event, when it is an order book one; null otherwise. */
    public OrderBookEventStateDTO getOrderBookState() {
        return orderBookState;
    }

    /** How this user stands in the selected event; null when none is selected. */
    public UserEventInvolvementDTO getInvolvement() {
        return involvement;
    }

    /** Only the chat lines the caller has not seen, so the poll stays small. */
    public List<ChatMessageDTO> getNewChatMessages() {
        return newChatMessages;
    }

    /** How many chat lines exist in total; the caller sends this back next time. */
    public int getChatTotal() {
        return chatTotal;
    }
}
