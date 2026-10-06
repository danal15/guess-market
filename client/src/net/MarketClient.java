package net;

import engine.api.GMEngine;
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
import java.util.ArrayList;
import java.util.List;

/**
 * The engine, as seen from the client: every call goes to the server over HTTP
 * and comes back as the same data transfer object the engine would have handed
 * over directly.
 *
 * It implements {@link GMEngine} on purpose. The screens from exercise 2 were
 * written against that interface, so they work here untouched - the trade forms,
 * the event details, the involvement panel - and the only thing that changed
 * underneath them is where the answer comes from. Where this class refuses to
 * work is for the three file-on-disk methods, which belong to a single user
 * sitting at the machine the engine runs on and mean nothing on a server.
 */
public class MarketClient implements GMEngine {

    private final Transport transport;
    private String userName;

    public MarketClient(String baseUrl) {
        this.transport = new Transport(baseUrl);
    }

    public String getBaseUrl() {
        return transport.getBaseUrl();
    }

    /** The logged in user, or null before anybody has logged in. */
    public String getUserName() {
        return userName;
    }

    // ---------- logging in and funding an account ----------

    @Override
    public UserDTO login(String name) {
        UserDTO user = transport.post("/login", UserDTO.class, "name", name);
        this.userName = user.getName();
        return user;
    }

    @Override
    public boolean knowsUser(String name) {
        // The server answers this for itself on every request; asking it
        // directly is only here to honour the interface.
        return userName != null && userName.equals(name);
    }

    @Override
    public UserDTO loadFunds(String name, double amount) {
        return transport.post("/funds", UserDTO.class, "amount", amount);
    }

    // ---------- the poll ----------

    @Override
    public SnapshotDTO getSnapshot(String name, Integer selectedEventId, int chatFrom) {
        return transport.get("/sync", SnapshotDTO.class,
                "eventId", selectedEventId, "chatFrom", chatFrom);
    }

    // ---------- uploading a file of events ----------

    /** Sends the bytes themselves, so the server never needs a path or a disk. */
    public UploadResult upload(String fileName, byte[] content) {
        return transport.upload("/upload", fileName, content, UploadResult.class);
    }

    @Override
    public List<String> uploadMarketFile(InputStream xml, String uploaderName) {
        throw unsupported("Use upload(fileName, content), which sends the file to the server.");
    }

    // ---------- taking part ----------

    @Override
    public void openEvent(int eventId, String actingUserName) {
        transport.post("/event/open", EventDTO.class, "eventId", eventId);
    }

    @Override
    public CloseResultDTO closeEvent(int eventId, String actingUserName, int winningOptionIndex) {
        return transport.post("/event/close", CloseResultDTO.class,
                "eventId", eventId, "winningOption", winningOptionIndex);
    }

    @Override
    public PurchaseQuoteDTO quoteLmsrPurchase(int eventId, String name, int optionIndex, long quantity) {
        return transport.get("/trade/lmsr-quote", PurchaseQuoteDTO.class,
                "eventId", eventId, "option", optionIndex, "quantity", quantity);
    }

    @Override
    public BuyResultDTO buyLmsrShares(int eventId, String name, int optionIndex, long quantity) {
        return transport.post("/trade/lmsr-buy", BuyResultDTO.class,
                "eventId", eventId, "option", optionIndex, "quantity", quantity);
    }

    @Override
    public OrderQuoteDTO quoteOrder(int eventId, String name, int optionIndex,
                                    OrderSide side, double price, long quantity) {
        return transport.get("/trade/order-quote", OrderQuoteDTO.class,
                "eventId", eventId, "option", optionIndex, "side", side.name(),
                "price", price, "quantity", quantity);
    }

    @Override
    public OrderResultDTO placeOrder(int eventId, String name, int optionIndex,
                                     OrderSide side, double price, long quantity) {
        return transport.post("/trade/order", OrderResultDTO.class,
                "eventId", eventId, "option", optionIndex, "side", side.name(),
                "price", price, "quantity", quantity);
    }

    @Override
    public EventDTO createEvent(NewEventRequestDTO request, String creatorUserName) {
        return transport.postJson("/event/new", request, EventDTO.class);
    }

    // ---------- chat ----------

    @Override
    public ChatMessageDTO postChatMessage(String name, String text) {
        return transport.post("/chat", ChatMessageDTO.class, "text", text);
    }

    // ---------- reads the snapshot already covers ----------

    /*
     * The client polls for everything on screen in one request, so these are
     * here to honour the interface rather than because the screens call them.
     * Each still works if something does, which keeps the contract honest.
     */

    @Override
    public List<EventDTO> getEvents(EventFilterDTO filter) {
        EventFilterDTO applied = filter == null ? EventFilterDTO.all() : filter;
        List<EventDTO> matching = new ArrayList<>();
        for (EventDTO event : snapshotNow().getEvents()) {
            if (applied.matches(event)) {
                matching.add(event);
            }
        }
        return matching;
    }

    @Override
    public EventDTO getEvent(int eventId) {
        for (EventDTO event : getEvents(EventFilterDTO.all())) {
            if (event.getId() == eventId) {
                return event;
            }
        }
        throw new IllegalArgumentException("No event with id " + eventId + " exists.");
    }

    @Override
    public LmsrEventStateDTO getLmsrEventState(int eventId) {
        return requireSelected(eventId).getLmsrState();
    }

    @Override
    public OrderBookEventStateDTO getOrderBookEventState(int eventId) {
        return requireSelected(eventId).getOrderBookState();
    }

    @Override
    public UserEventInvolvementDTO getUserInvolvement(String name, int eventId) {
        return requireSelected(eventId).getInvolvement();
    }

    @Override
    public List<UserDTO> getUsers() {
        return snapshotNow().getUsers();
    }

    @Override
    public UserDTO getUser(String name) {
        return snapshotNow().getUser();
    }

    @Override
    public List<EventDTO> getUserEvents(String name) {
        return getEvents(EventFilterDTO.all());
    }

    @Override
    public boolean isParticipant(String name, int eventId) {
        return snapshotNow().getParticipatingEventIds().contains(eventId);
    }

    @Override
    public List<Integer> getParticipatingEventIds(String name) {
        return snapshotNow().getParticipatingEventIds();
    }

    @Override
    public List<Double> getUserBalanceHistory(String name) {
        return snapshotNow().getBalanceHistory();
    }

    @Override
    public List<MovementDTO> getAccountMovements(String name) {
        return snapshotNow().getMovements();
    }

    @Override
    public boolean isLoaded() {
        return snapshotNow().getTotalEventCount() > 0;
    }

    private SnapshotDTO snapshotNow() {
        return getSnapshot(userName, null, Integer.MAX_VALUE);
    }

    private SnapshotDTO requireSelected(int eventId) {
        return getSnapshot(userName, eventId, Integer.MAX_VALUE);
    }

    // ---------- things only a local engine can do ----------

    @Override
    public void loadMarketFile(String path) {
        throw unsupported("Files are uploaded to the server, not read from a path on it.");
    }

    @Override
    public void saveState(String pathWithoutExtension) {
        throw unsupported("The market lives only while the server is running, by design.");
    }

    @Override
    public void loadState(String pathWithoutExtension) {
        throw unsupported("The market lives only while the server is running, by design.");
    }

    @Override
    public String getLoadedFilePath() {
        return null;
    }

    private UnsupportedOperationException unsupported(String why) {
        return new UnsupportedOperationException(why);
    }

    /** What the server says after a file has been taken in. */
    public static final class UploadResult {
        private String fileName;
        private List<String> eventNames;

        public String getFileName() {
            return fileName;
        }

        public List<String> getEventNames() {
            return eventNames == null ? List.of() : eventNames;
        }
    }
}
