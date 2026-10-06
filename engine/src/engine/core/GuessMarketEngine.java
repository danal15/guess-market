package engine.core;

import engine.api.GMEngine;
import engine.api.dto.BuyResultDTO;
import engine.api.dto.ChatMessageDTO;
import engine.api.dto.CloseResultDTO;
import engine.api.dto.EventDTO;
import engine.api.dto.EventFilterDTO;
import engine.api.dto.FillDTO;
import engine.api.dto.LmsrEventStateDTO;
import engine.api.dto.MovementDTO;
import engine.api.dto.NewEventRequestDTO;
import engine.api.dto.OptionStateDTO;
import engine.api.dto.OrderBookEventStateDTO;
import engine.api.dto.OrderBookSideDTO;
import engine.api.dto.OrderBookStatsDTO;
import engine.api.dto.OrderDTO;
import engine.api.dto.OrderQuoteDTO;
import engine.api.dto.OrderResultDTO;
import engine.api.dto.ParticipantDTO;
import engine.api.dto.PricePointDTO;
import engine.api.dto.PurchaseQuoteDTO;
import engine.api.dto.SnapshotDTO;
import engine.api.dto.TradeDTO;
import engine.api.dto.UserDTO;
import engine.api.dto.UserEventInvolvementDTO;
import engine.api.exception.TradingException;
import engine.api.exception.StatePersistenceException;
import engine.core.ob.OrderMatcher;
import engine.core.ob.OrderOutcome;
import engine.core.xml.MarketFileLoader;
import engine.model.CloseSummary;
import engine.model.ChatMessage;
import engine.model.CommissionType;
import engine.model.Event;
import engine.model.ExecutedTrade;
import engine.model.Holding;
import engine.model.LmsrEvent;
import engine.model.Movement;
import engine.model.Order;
import engine.model.OrderBook;
import engine.model.OrderBookEvent;
import engine.model.OrderSide;
import engine.model.Trade;
import engine.model.User;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one implementation of the engine. It is passive: it answers whoever calls
 * it and knows nothing about them, which is what lets the same engine sit
 * behind a desktop window in exercise 2 and behind a web server in exercise 3.
 *
 * Every public method is synchronised. On a server several requests arrive at
 * once, and almost every action here is a transaction over several objects at
 * once - take the money, move the shares, bump the counters, append the trade.
 * Guarding each field on its own would be more code and still be wrong, so the
 * whole api is the critical section. Nothing below this class needs to know.
 */
public class GuessMarketEngine implements GMEngine {

    private static final String STATE_FILE_EXTENSION = ".gmstate";

    /** What a user has in their account the moment they log in. */
    private static final double STARTING_BALANCE = 0.0;

    private Market market;
    private String loadedFilePath;

    /** Starts with nothing loaded, the way the desktop application begins. */
    public GuessMarketEngine() {
    }

    /**
     * Starts with an empty market that is open for business straight away. The
     * server needs this: users log in and upload files into a market that has
     * to exist before the first of them arrives.
     */
    public static GuessMarketEngine startedEmpty() {
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.market = new Market(List.of(), List.of());
        return engine;
    }

    // ---------- loading ----------

    @Override
    public synchronized void loadMarketFile(String path) {
        Market loaded = MarketFileLoader.load(path);
        this.market = loaded;
        this.loadedFilePath = path;
    }

    /**
     * Exercise 3 files add to the market instead of replacing it, so several
     * people can each contribute events to one running market.
     */
    @Override
    public synchronized List<String> uploadMarketFile(InputStream xml, String uploaderName) {
        requireLoaded();
        User uploader = market.requireUser(uploaderName);
        // Uploading makes the uploader market maker of everything in the file,
        // which is an action like any other and is not open to a blocked account.
        uploader.requireActive();
        List<Event> added = MarketFileLoader.loadInto(market, xml, uploader.getName());
        List<String> names = new ArrayList<>();
        for (Event event : added) {
            uploader.addMarketMakerEvent(event.getId());
            names.add(event.getName());
        }
        return names;
    }

    // ---------- users arriving and funding themselves ----------

    @Override
    public synchronized UserDTO login(String userName) {
        requireLoaded();
        String name = userName == null ? "" : userName.trim();
        if (name.isEmpty()) {
            throw new TradingException("Enter a user name to log in with.");
        }
        if (market.hasUserNamed(name)) {
            throw new TradingException("The name '" + name
                    + "' is already taken. Pick another one and log in again.");
        }
        User user = new User(name, STARTING_BALANCE);
        market.addUser(user);
        return toUserDTO(user);
    }

    @Override
    public synchronized boolean knowsUser(String userName) {
        return market != null && market.hasUserNamed(userName);
    }

    @Override
    public synchronized UserDTO loadFunds(String userName, double amount) {
        requireLoaded();
        User user = market.requireUser(userName);
        if (!(amount > 0)) {
            throw new TradingException("The amount to load must be greater than zero.");
        }
        if (Double.isInfinite(amount) || Double.isNaN(amount)) {
            throw new TradingException("That is not an amount of money.");
        }
        user.loadFunds(amount);
        return toUserDTO(user);
    }

    @Override
    public synchronized List<MovementDTO> getAccountMovements(String userName) {
        requireLoaded();
        List<MovementDTO> lines = new ArrayList<>();
        int index = 1;
        for (Movement movement : market.requireUser(userName).getMovements()) {
            lines.add(new MovementDTO(index++, movement.getReason(), movement.getEventName(),
                    movement.getAmount(), movement.getBalanceAfter()));
        }
        return lines;
    }

    @Override
    public synchronized List<Integer> getParticipatingEventIds(String userName) {
        requireLoaded();
        return new ArrayList<>(market.requireUser(userName).getParticipatingEventIds());
    }

    // ---------- the whole of one screen, in one consistent pass ----------

    @Override
    public synchronized SnapshotDTO getSnapshot(String userName, Integer selectedEventId, int chatFrom) {
        requireLoaded();
        User user = market.requireUser(userName);

        LmsrEventStateDTO lmsrState = null;
        OrderBookEventStateDTO orderBookState = null;
        UserEventInvolvementDTO involvement = null;
        // A selection can name an event that has since been replaced by a fresh
        // file, so a stale id is answered with nothing rather than an error.
        if (selectedEventId != null) {
            Event selected = market.findById(selectedEventId).orElse(null);
            if (selected != null) {
                if (selected instanceof OrderBookEvent book) {
                    orderBookState = toOrderBookState(book);
                } else {
                    lmsrState = toLmsrState((LmsrEvent) selected);
                }
                involvement = getUserInvolvement(user.getName(), selected.getId());
            }
        }

        return new SnapshotDTO(
                toUserDTO(user),
                getUsers(),
                getEvents(EventFilterDTO.all()),
                market.getEvents().size(),
                getAccountMovements(user.getName()),
                getUserBalanceHistory(user.getName()),
                getParticipatingEventIds(user.getName()),
                lmsrState, orderBookState, involvement,
                chatSince(chatFrom), market.getChat().size());
    }

    // ---------- chat ----------

    private static final DateTimeFormatter CHAT_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Longer than this and one person could push everybody else off the screen. */
    private static final int CHAT_MESSAGE_LIMIT = 500;

    @Override
    public synchronized ChatMessageDTO postChatMessage(String userName, String text) {
        requireLoaded();
        User user = market.requireUser(userName);
        user.requireActive();
        String said = text == null ? "" : text.trim();
        if (said.isEmpty()) {
            throw new TradingException("There is nothing to send.");
        }
        if (said.length() > CHAT_MESSAGE_LIMIT) {
            throw new TradingException("That message is too long - keep it under "
                    + CHAT_MESSAGE_LIMIT + " characters.");
        }
        ChatMessage message = new ChatMessage(user.getName(), said, System.currentTimeMillis());
        market.addChatMessage(message);
        return toChatDTO(message);
    }

    /** Only what the caller has not seen, so a long conversation costs nothing to follow. */
    private List<ChatMessageDTO> chatSince(int from) {
        List<ChatMessage> all = market.getChat();
        List<ChatMessageDTO> fresh = new ArrayList<>();
        for (int i = Math.max(0, Math.min(from, all.size())); i < all.size(); i++) {
            fresh.add(toChatDTO(all.get(i)));
        }
        return fresh;
    }

    private ChatMessageDTO toChatDTO(ChatMessage message) {
        String time = Instant.ofEpochMilli(message.getTimestamp())
                .atZone(ZoneId.systemDefault()).format(CHAT_CLOCK);
        return new ChatMessageDTO(message.getUserName(), message.getText(), time);
    }

    @Override
    public synchronized void saveState(String pathWithoutExtension) {
        requireLoaded();
        java.io.File target = new java.io.File(requirePath(pathWithoutExtension) + STATE_FILE_EXTENSION);
        java.io.File folder = target.getAbsoluteFile().getParentFile();
        if (folder != null && !folder.exists()) {
            throw new StatePersistenceException("Cannot save: the folder " + folder + " does not exist.");
        }
        try (java.io.ObjectOutputStream out =
                     new java.io.ObjectOutputStream(new java.io.FileOutputStream(target))) {
            out.writeObject(market);
        } catch (java.io.IOException e) {
            throw new StatePersistenceException("Could not save to " + target + ": " + e.getMessage());
        }
    }

    @Override
    public synchronized void loadState(String pathWithoutExtension) {
        java.io.File source = new java.io.File(requirePath(pathWithoutExtension) + STATE_FILE_EXTENSION);
        if (!source.exists() || !source.isFile()) {
            throw new StatePersistenceException("No saved market exists at: " + source);
        }
        Object loaded;
        try (java.io.ObjectInputStream in =
                     new java.io.ObjectInputStream(new java.io.FileInputStream(source))) {
            loaded = in.readObject();
        } catch (java.io.InvalidClassException e) {
            throw new StatePersistenceException(
                    "The file " + source + " was saved by a different version of this program.");
        } catch (java.io.IOException | ClassNotFoundException e) {
            throw new StatePersistenceException(
                    "The file " + source + " is not a saved market: " + e.getMessage());
        }
        if (!(loaded instanceof Market)) {
            throw new StatePersistenceException("The file " + source + " is not a saved market.");
        }
        this.market = (Market) loaded;
    }

    private String requirePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new StatePersistenceException("No file path was given.");
        }
        return path.trim();
    }

    @Override
    public synchronized boolean isLoaded() {
        return market != null;
    }

    @Override
    public synchronized String getLoadedFilePath() {
        return loadedFilePath;
    }

    // ---------- events ----------

    @Override
    public synchronized List<EventDTO> getEvents(EventFilterDTO filter) {
        requireLoaded();
        List<EventDTO> result = new ArrayList<>();
        EventFilterDTO applied = filter == null ? EventFilterDTO.all() : filter;
        for (Event event : market.getEvents()) {
            EventDTO candidate = toEventDTO(event);
            if (applied.matches(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    @Override
    public synchronized EventDTO getEvent(int eventId) {
        requireLoaded();
        return toEventDTO(market.requireEvent(eventId));
    }

    @Override
    public synchronized LmsrEventStateDTO getLmsrEventState(int eventId) {
        requireLoaded();
        return toLmsrState(requireLmsr(market.requireEvent(eventId)));
    }

    @Override
    public synchronized OrderBookEventStateDTO getOrderBookEventState(int eventId) {
        requireLoaded();
        return toOrderBookState(requireOrderBook(market.requireEvent(eventId)));
    }

    // ---------- users ----------

    @Override
    public synchronized List<UserDTO> getUsers() {
        requireLoaded();
        List<UserDTO> result = new ArrayList<>();
        for (User user : market.getUsers()) {
            result.add(toUserDTO(user));
        }
        return result;
    }

    @Override
    public synchronized UserDTO getUser(String userName) {
        requireLoaded();
        return toUserDTO(market.requireUser(userName));
    }

    @Override
    public synchronized List<EventDTO> getUserEvents(String userName) {
        requireLoaded();
        // Every event, not only the ones already joined: a user has to be able
        // to reach an event in order to take part in it for the first time.
        market.requireUser(userName);
        return getEvents(EventFilterDTO.all());
    }

    @Override
    public synchronized boolean isParticipant(String userName, int eventId) {
        requireLoaded();
        return market.requireUser(userName).participatesIn(eventId);
    }

    @Override
    public synchronized UserEventInvolvementDTO getUserInvolvement(String userName, int eventId) {
        requireLoaded();
        User user = market.requireUser(userName);
        Event event = market.requireEvent(eventId);
        Holding holding = user.existingHolding(eventId);

        String winner = winningOptionName(event);
        Double profitOrLoss = null;
        Double tradingResult = null;
        if (winner != null && holding != null) {
            profitOrLoss = holding.netCashFlow();
            tradingResult = holding.tradingResult();
        }

        List<TradeDTO> trades = new ArrayList<>();
        if (event instanceof LmsrEvent lmsr) {
            List<Trade> own = lmsr.getTradesOf(userName);
            for (int i = own.size() - 1; i >= 0; i--) {
                trades.add(toTradeDTO(own.get(i)));
            }
        }

        int openOrders = 0;
        long openQuantity = 0;
        if (event instanceof OrderBookEvent book) {
            for (int i = 0; i < 2; i++) {
                for (Order order : book.book(i).getBids()) {
                    if (order.getUserName().equals(userName)) {
                        openOrders++;
                        openQuantity += order.getRemainingQuantity();
                    }
                }
                for (Order order : book.book(i).getAsks()) {
                    if (order.getUserName().equals(userName)) {
                        openOrders++;
                        openQuantity += order.getRemainingQuantity();
                    }
                }
            }
        }

        return new UserEventInvolvementDTO(
                event.getId(), event.getName(), event.getMethod().getLabel(),
                event instanceof OrderBookEvent, event.getStatus().getLabel(),
                event.isMarketMaker(userName),
                holding == null ? 0.0 : holding.getCommissionPaid(),
                trades,
                event.getOption(0).getName(),
                holding == null ? 0L : holding.getQuantity(0),
                holding == null ? 0.0 : holding.getAmountPaid(0),
                event.getOption(1).getName(),
                holding == null ? 0L : holding.getQuantity(1),
                holding == null ? 0.0 : holding.getAmountPaid(1),
                winner, profitOrLoss, tradingResult,
                holding == null ? 0.0 : holding.getMarketMakerPaid(),
                holding == null ? 0.0 : holding.getMarketMakerReceived(),
                openOrders, openQuantity);
    }

    @Override
    public synchronized List<Double> getUserBalanceHistory(String userName) {
        requireLoaded();
        // A copy, not the market's own list: the caller reads it after the lock
        // has gone, and by then another request may be appending to it.
        return new ArrayList<>(market.requireUser(userName).getBalanceHistory());
    }

    // ---------- lifecycle ----------

    @Override
    public synchronized void openEvent(int eventId, String actingUserName) {
        requireLoaded();
        Event event = market.requireEvent(eventId);
        event.open(market.requireUser(actingUserName));
    }

    @Override
    public synchronized CloseResultDTO closeEvent(int eventId, String actingUserName, int winningOptionIndex) {
        requireLoaded();
        Event event = market.requireEvent(eventId);
        CloseSummary summary = event.close(
                market.requireUser(actingUserName), winningOptionIndex, market.getUsers());
        return new CloseResultDTO(event.getName(), summary.getWinningOptionName(),
                summary.getWinnersPaid(), summary.getTotalPaidOut(), summary.getCommissionCollected(),
                summary.getReturnedToMarketMaker(), summary.getCancelledOrders());
    }

    // ---------- trading ----------

    @Override
    public synchronized PurchaseQuoteDTO quoteLmsrPurchase(int eventId, String userName, int optionIndex, long quantity) {
        requireLoaded();
        LmsrEvent event = requireLmsr(market.requireEvent(eventId));
        User buyer = market.requireUser(userName);

        double[] quote = event.quote(optionIndex, quantity);
        double balance = buyer.getAccount().getBalance();
        return new PurchaseQuoteDTO(quote[0], quote[1], quote[2],
                quote[0] / quantity, quote[3], balance, buyer.canAfford(quote[2]),
                quote[2] >= LmsrEvent.SMALLEST_CHARGE);
    }

    @Override
    public synchronized BuyResultDTO buyLmsrShares(int eventId, String userName, int optionIndex, long quantity) {
        requireLoaded();
        LmsrEvent event = requireLmsr(market.requireEvent(eventId));
        User buyer = market.requireUser(userName);
        User marketMaker = market.marketMakerOf(event);

        double[] result = event.buy(buyer, marketMaker, optionIndex, quantity);
        return new BuyResultDTO(result[0], result[1], result[2],
                buyer.getAccount().getBalance(), buyer.isBlocked(), toLmsrState(event));
    }

    @Override
    public synchronized OrderQuoteDTO quoteOrder(int eventId, String userName, int optionIndex,
                                    OrderSide side, double price, long quantity) {
        requireLoaded();
        OrderBookEvent event = requireOrderBook(market.requireEvent(eventId));
        User trader = market.requireUser(userName);
        if (optionIndex != 0 && optionIndex != 1) {
            throw new IllegalArgumentException("Option index must be 0 or 1.");
        }

        boolean buying = side == OrderSide.BUY;
        double orderValue = quantity * price;
        // Commission is charged to buyers only, and only when the event
        // collects it on purchase. It is a maximum: a fill at a better price
        // costs less.
        double commission = buying && event.getCommissionType() == CommissionType.ON_PURCHASE
                ? orderValue * event.getCommissionPercent() / 100.0
                : 0.0;
        double total = buying ? orderValue + commission : orderValue;

        Holding holding = trader.existingHolding(eventId);
        long held = holding == null ? 0L : holding.getQuantity(optionIndex);
        double maxPrice = event.maxPrice();
        boolean priceValid = price >= 0.01 - 1e-9 && price <= maxPrice + 1e-9
                && Math.abs(price * 100 - Math.round(price * 100)) <= 1e-6;

        // The best deal on offer right now, and how much of it there is.
        double[] best = bestAvailable(event, optionIndex, buying);
        Double opposingPrice = best == null ? null : best[0];
        long availableQuantity = best == null ? 0L : (long) best[1];
        boolean wouldTrade = best != null
                && (buying ? best[0] <= price + 1e-9 : best[0] >= price - 1e-9);

        return new OrderQuoteDTO(buying, orderValue, commission, total,
                trader.getAccount().getBalance(), held,
                !buying || trader.canAfford(total),
                buying || held >= quantity,
                maxPrice, priceValid, opposingPrice, wouldTrade, availableQuantity,
                event.isAllowMint());
    }

    /**
     * The keenest price a trader could get right now and how many shares are
     * there at it, as {price, quantity}, or null when the other side is empty.
     *
     * A buyer can be served two ways: by an ask on the same option, or - when
     * the event allows minting - by a bid on the opposite option, which costs
     * the rest of the base value. The higher that opposite bid, the less the
     * buyer pays, so the two sources have to be compared rather than simply
     * taking the top of one book.
     */
    private double[] bestAvailable(OrderBookEvent event, int optionIndex, boolean buying) {
        double bestPrice = buying ? Double.MAX_VALUE : -1.0;
        long quantity = 0;

        if (buying) {
            for (Order ask : event.book(optionIndex).getAsks()) {
                bestPrice = Math.min(bestPrice, ask.getPrice());
            }
            if (event.isAllowMint()) {
                for (Order bid : event.book(1 - optionIndex).getBids()) {
                    bestPrice = Math.min(bestPrice, event.getD() - bid.getPrice());
                }
            }
            if (bestPrice == Double.MAX_VALUE) {
                return null;
            }
            for (Order ask : event.book(optionIndex).getAsks()) {
                if (Math.abs(ask.getPrice() - bestPrice) < 1e-9) {
                    quantity += ask.getRemainingQuantity();
                }
            }
            if (event.isAllowMint()) {
                for (Order bid : event.book(1 - optionIndex).getBids()) {
                    if (Math.abs((event.getD() - bid.getPrice()) - bestPrice) < 1e-9) {
                        quantity += bid.getRemainingQuantity();
                    }
                }
            }
        } else {
            // A seller can only meet a bid on the same option; minting is a
            // way of creating shares, not of disposing of them.
            for (Order bid : event.book(optionIndex).getBids()) {
                bestPrice = Math.max(bestPrice, bid.getPrice());
            }
            if (bestPrice < 0) {
                return null;
            }
            for (Order bid : event.book(optionIndex).getBids()) {
                if (Math.abs(bid.getPrice() - bestPrice) < 1e-9) {
                    quantity += bid.getRemainingQuantity();
                }
            }
        }
        return new double[] { bestPrice, quantity };
    }

    @Override
    public synchronized OrderResultDTO placeOrder(int eventId, String userName, int optionIndex,
                                     OrderSide side, double price, long quantity) {
        requireLoaded();
        OrderBookEvent event = requireOrderBook(market.requireEvent(eventId));
        User trader = market.requireUser(userName);
        User marketMaker = market.marketMakerOf(event);

        OrderOutcome outcome = new OrderMatcher(market, event, trader, marketMaker)
                .submit(optionIndex, side, price, quantity);

        List<FillDTO> fills = new ArrayList<>();
        for (OrderOutcome.Fill fill : outcome.getFills()) {
            fills.add(new FillDTO(fill.getQuantity(), fill.getPrice(),
                    fill.getCounterpartyName(), fill.getKindLabel()));
        }

        return new OrderResultDTO(fills, outcome.getMintedQuantity(), outcome.getRestingQuantity(),
                outcome.getCashPaid(), outcome.getCashReceived(), outcome.getCommissionPaid(),
                trader.getAccount().getBalance(), outcome.isTraderBlocked(), toOrderBookState(event));
    }

    // ---------- creating an event (bonus) ----------

    @Override
    public synchronized EventDTO createEvent(NewEventRequestDTO request, String creatorUserName) {
        requireLoaded();
        User creator = market.requireUser(creatorUserName);
        creator.requireActive();

        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new TradingException("The event needs a name.");
        }
        if (market.hasEventNamed(name)) {
            throw new TradingException("An event named '" + name + "' already exists.");
        }
        if (request.getCommissionPercent() < 0 || request.getCommissionPercent() > 90) {
            throw new TradingException("Commission must be between 0 and 90.");
        }
        CommissionType commissionType = CommissionType.fromXmlValue(request.getCommissionTypeLabel());
        if (commissionType == null) {
            throw new TradingException("Commission type must be on-purchase or on-close.");
        }
        String option1 = request.getOption1Name() == null ? "" : request.getOption1Name().trim();
        String option2 = request.getOption2Name() == null ? "" : request.getOption2Name().trim();
        if (option1.isEmpty() || option2.isEmpty()) {
            throw new TradingException("Both option names are required.");
        }

        int id = market.nextEventId();
        Event event;
        if (request.isOrderBook()) {
            if (request.getBaseValue() <= 0) {
                throw new TradingException("The base value d must be a positive whole number.");
            }
            if (request.getInitialInvestment() < 0) {
                throw new TradingException("The initial investment cannot be negative.");
            }
            event = new OrderBookEvent(id, name, request.getDescription(), request.getCommissionPercent(),
                    commissionType, creator.getName(), option1, option2,
                    request.getBaseValue(), request.isAllowMint(), request.getInitialInvestment());
        } else {
            if (request.getB() <= 0) {
                throw new TradingException("The liquidity value b must be a positive whole number.");
            }
            event = new LmsrEvent(id, name, request.getDescription(), request.getCommissionPercent(),
                    commissionType, creator.getName(), option1, option2, request.getB());
        }

        market.addEvent(event);
        creator.addMarketMakerEvent(id);
        return toEventDTO(event);
    }

    // ---------- guards ----------

    private void requireLoaded() {
        if (market == null) {
            throw new IllegalStateException("No valid file is loaded yet - load a market file first.");
        }
    }

    private LmsrEvent requireLmsr(Event event) {
        if (!(event instanceof LmsrEvent lmsr)) {
            throw new IllegalArgumentException("Event '" + event.getName() + "' is not an LMSR event.");
        }
        return lmsr;
    }

    private OrderBookEvent requireOrderBook(Event event) {
        if (!(event instanceof OrderBookEvent orderBook)) {
            throw new IllegalArgumentException("Event '" + event.getName() + "' is not an order book event.");
        }
        return orderBook;
    }

    // ---------- model to DTO ----------

    private EventDTO toEventDTO(Event event) {
        return new EventDTO(event.getId(), event.getName(), event.getDescription(),
                event.getCommissionPercent(), event.getCommissionType().getLabel(),
                event.getMethod().getLabel(), event instanceof OrderBookEvent,
                event.getStatus().getLabel(), event.getMarketMakerName(),
                event.getOption(0).getName(), event.getOption(1).getName(),
                event.getAccount().getBalance(), event.requiredOpeningFunds());
    }

    private UserDTO toUserDTO(User user) {
        return new UserDTO(user.getName(), user.getAccount().getBalance(),
                user.isBlocked(), user.isMarketMaker());
    }

    private TradeDTO toTradeDTO(Trade trade) {
        return new TradeDTO(trade.getUserName(), trade.getOptionName(), trade.getQuantity(),
                trade.getSharesCost(), trade.getCommissionPaid());
    }

    private String winningOptionName(Event event) {
        Integer winning = event.getWinningOptionIndex();
        return winning == null ? null : event.getOption(winning).getName();
    }

    private LmsrEventStateDTO toLmsrState(LmsrEvent event) {
        OptionStateDTO first = new OptionStateDTO(event.getOption(0).getName(),
                event.priceOf(0), event.getOption(0).getSharesOutstanding());
        OptionStateDTO second = new OptionStateDTO(event.getOption(1).getName(),
                event.priceOf(1), event.getOption(1).getSharesOutstanding());

        List<Trade> trades = event.getTrades();
        List<TradeDTO> newestFirst = new ArrayList<>();
        for (int i = trades.size() - 1; i >= 0; i--) {
            newestFirst.add(toTradeDTO(trades.get(i)));
        }

        List<PricePointDTO> history = new ArrayList<>();
        history.add(new PricePointDTO(0, 0.5, 0.5));
        int index = 1;
        for (double[] point : event.getPriceHistory()) {
            history.add(new PricePointDTO(index++, point[0], point[1]));
        }

        return new LmsrEventStateDTO(toEventDTO(event), first, second,
                event.getAccount().getBalance(), event.getTotalCommissionCollected(),
                Collections.unmodifiableList(newestFirst), winningOptionName(event),
                Collections.unmodifiableList(history));
    }

    private OrderBookEventStateDTO toOrderBookState(OrderBookEvent event) {
        return new OrderBookEventStateDTO(toEventDTO(event), event.getD(), event.isAllowMint(),
                event.getAccount().getBalance(), event.getTotalCommissionCollected(),
                toBookSide(event, 0), toBookSide(event, 1),
                participantsOf(event), winningOptionName(event), orderBookPriceHistory(event));
    }

    private OrderBookSideDTO toBookSide(OrderBookEvent event, int optionIndex) {
        OrderBook book = event.book(optionIndex);
        OrderBookStatsDTO stats = new OrderBookStatsDTO(book.getLastTradePrice(),
                book.getBestBidPrice(), book.getBestAskPrice(), book.getMidPrice(), book.getSpread());
        return new OrderBookSideDTO(event.getOption(optionIndex).getName(),
                toOrderDTOs(book.getBids()), toOrderDTOs(book.getAsks()), stats,
                event.getOption(optionIndex).getSharesOutstanding());
    }

    private List<OrderDTO> toOrderDTOs(List<Order> orders) {
        List<OrderDTO> result = new ArrayList<>();
        for (Order order : orders) {
            result.add(new OrderDTO(order.getUserName(), order.getSide().getLabel(),
                    order.getRemainingQuantity(), order.getPrice()));
        }
        return result;
    }

    private List<ParticipantDTO> participantsOf(OrderBookEvent event) {
        double price1 = event.book(0).referencePrice(event.getD());
        double price2 = event.book(1).referencePrice(event.getD());

        List<ParticipantDTO> result = new ArrayList<>();
        for (User user : market.getUsers()) {
            Holding holding = user.existingHolding(event.getId());
            if (holding == null) {
                continue;
            }
            boolean hasResting = hasRestingOrders(event, user.getName());
            if (!holding.holdsAnything() && !hasResting && !holding.hasEverOrdered()) {
                continue;
            }
            result.add(new ParticipantDTO(user.getName(),
                    holding.getQuantity(0), holding.getQuantity(0) * price1,
                    holding.getQuantity(1), holding.getQuantity(1) * price2,
                    hasResting));
        }
        return result;
    }

    private boolean hasRestingOrders(OrderBookEvent event, String userName) {
        for (int i = 0; i < 2; i++) {
            for (Order order : event.book(i).getBids()) {
                if (order.getUserName().equals(userName)) {
                    return true;
                }
            }
            for (Order order : event.book(i).getAsks()) {
                if (order.getUserName().equals(userName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One point per executed trade, carrying the latest known price of each option. */
    private List<PricePointDTO> orderBookPriceHistory(OrderBookEvent event) {
        List<PricePointDTO> history = new ArrayList<>();
        double price1 = event.getD() / 2.0;
        double price2 = event.getD() / 2.0;
        int index = 0;
        history.add(new PricePointDTO(index++, price1, price2));
        for (ExecutedTrade trade : event.getExecutedTrades()) {
            if (trade.getKind() == ExecutedTrade.Kind.INITIAL_ALLOCATION) {
                continue;
            }
            if (trade.getOptionIndex() == 0) {
                price1 = trade.getPrice();
            } else {
                price2 = trade.getPrice();
            }
            history.add(new PricePointDTO(index++, price1, price2));
        }
        return history;
    }
}
