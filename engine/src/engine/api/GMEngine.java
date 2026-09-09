package engine.api;

import engine.api.dto.BuyResultDTO;
import engine.api.dto.CloseResultDTO;
import engine.api.dto.EventDTO;
import engine.api.dto.EventFilterDTO;
import engine.api.dto.LmsrEventStateDTO;
import engine.api.dto.NewEventRequestDTO;
import engine.api.dto.OrderBookEventStateDTO;
import engine.api.dto.OrderQuoteDTO;
import engine.api.dto.OrderResultDTO;
import engine.api.dto.PurchaseQuoteDTO;
import engine.api.dto.UserDTO;
import engine.api.dto.UserEventInvolvementDTO;
import engine.model.OrderSide;

import java.util.List;

/**
 * Everything a user interface can ask of the system. Option indexes are
 * zero based on this boundary; the interface never sees engine internals,
 * only immutable data transfer objects.
 */
public interface GMEngine {

    void loadMarketFile(String path);

    boolean isLoaded();

    String getLoadedFilePath();

    List<EventDTO> getEvents(EventFilterDTO filter);

    EventDTO getEvent(int eventId);

    LmsrEventStateDTO getLmsrEventState(int eventId);

    OrderBookEventStateDTO getOrderBookEventState(int eventId);

    List<UserDTO> getUsers();

    UserDTO getUser(String userName);

    List<EventDTO> getUserEvents(String userName);

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
