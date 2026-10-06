package engine.api;

import engine.api.dto.BuyResultDTO;
import engine.api.dto.ChatMessageDTO;
import engine.api.dto.CloseResultDTO;
import engine.api.dto.EventDTO;
import engine.api.dto.EventFilterDTO;
import engine.api.dto.LmsrEventStateDTO;
import engine.api.dto.MovementDTO;
import engine.api.dto.NewEventRequestDTO;
import engine.api.dto.OrderBookEventStateDTO;
import engine.api.dto.OrderQuoteDTO;
import engine.api.dto.OrderResultDTO;
import engine.api.dto.PurchaseQuoteDTO;
import engine.api.dto.SnapshotDTO;
import engine.api.dto.UserDTO;
import engine.api.dto.UserEventInvolvementDTO;
import engine.model.OrderSide;

import java.io.InputStream;
import java.util.List;

/**
 * Everything a user interface can ask of the system. Option indexes are
 * zero based on this boundary; the interface never sees engine internals,
 * only immutable data transfer objects.
 */
public interface GMEngine {

    void loadMarketFile(String path);

    /**
     * Adds the events of an exercise 3 file to the market, with the uploading
     * user as their market maker. Unlike {@link #loadMarketFile} this adds to
     * what is already there instead of replacing it, because in exercise 3
     * several people upload to one market. Nothing is added unless the whole
     * file is valid.
     *
     * @return the names of the events that were added
     */
    List<String> uploadMarketFile(InputStream xml, String uploaderName);

    /**
     * Registers a user by name and returns them. The name must not already be
     * taken; there are no passwords.
     */
    UserDTO login(String userName);

    /** Whether anybody is logged in under this name. */
    boolean knowsUser(String userName);

    /** Money the user puts into their own account. Lifts a block if there is one. */
    UserDTO loadFunds(String userName, double amount);

    /** Every movement of money in and out of this user's account, oldest first. */
    List<MovementDTO> getAccountMovements(String userName);

    /**
     * The ids of the events this user has acted in. One call, so a screen that
     * lists events can label each row without asking about them one at a time.
     */
    List<Integer> getParticipatingEventIds(String userName);

    /**
     * Everything one user's screen needs, gathered in one pass so that what
     * they see describes a single moment in the market.
     *
     * Every event comes back, not a filtered slice: the screens narrow the list
     * down themselves with {@link EventFilterDTO#matches}, so one answer serves
     * the events screen and the account screen at once.
     *
     * @param selectedEventId the event whose details are on screen, or null
     * @param chatFrom        how many chat lines the caller already has
     */
    SnapshotDTO getSnapshot(String userName, Integer selectedEventId, int chatFrom);

    /** Bonus: says something to everyone else in the market. */
    ChatMessageDTO postChatMessage(String userName, String text);

    /** Writes the whole market to a file so it can be picked up again later. */
    void saveState(String pathWithoutExtension);

    /** Replaces the loaded market with one written by saveState. */
    void loadState(String pathWithoutExtension);

    boolean isLoaded();

    String getLoadedFilePath();

    List<EventDTO> getEvents(EventFilterDTO filter);

    EventDTO getEvent(int eventId);

    LmsrEventStateDTO getLmsrEventState(int eventId);

    OrderBookEventStateDTO getOrderBookEventState(int eventId);

    List<UserDTO> getUsers();

    UserDTO getUser(String userName);

    /** Every event, so a user can reach one they have not taken part in yet. */
    List<EventDTO> getUserEvents(String userName);

    /** Whether this user has already acted in this event. */
    boolean isParticipant(String userName, int eventId);

    UserEventInvolvementDTO getUserInvolvement(String userName, int eventId);

    List<Double> getUserBalanceHistory(String userName);

    void openEvent(int eventId, String actingUserName);

    CloseResultDTO closeEvent(int eventId, String actingUserName, int winningOptionIndex);

    /** Works out the cost of a purchase without making it. */
    PurchaseQuoteDTO quoteLmsrPurchase(int eventId, String userName, int optionIndex, long quantity);

    BuyResultDTO buyLmsrShares(int eventId, String userName, int optionIndex, long quantity);

    /** Works out what an order would cost or bring in, without submitting it. */
    OrderQuoteDTO quoteOrder(int eventId, String userName, int optionIndex,
                             OrderSide side, double price, long quantity);

    OrderResultDTO placeOrder(int eventId, String userName, int optionIndex,
                              OrderSide side, double price, long quantity);

    EventDTO createEvent(NewEventRequestDTO request, String creatorUserName);
}
