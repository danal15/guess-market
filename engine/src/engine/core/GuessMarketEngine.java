package engine.core;

import engine.api.GMEngine;
import engine.api.dto.BuyResultDTO;
import engine.api.dto.CloseResultDTO;
import engine.api.dto.EventDTO;
import engine.api.dto.EventFilterDTO;
import engine.api.dto.FillDTO;
import engine.api.dto.LmsrEventStateDTO;
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
import engine.api.dto.TradeDTO;
import engine.api.dto.UserDTO;
import engine.api.dto.UserEventInvolvementDTO;
import engine.api.exception.TradingException;
import engine.core.ob.OrderMatcher;
import engine.core.ob.OrderOutcome;
import engine.core.xml.MarketFileLoader;
import engine.model.CloseSummary;
import engine.model.CommissionType;
import engine.model.Event;
import engine.model.ExecutedTrade;
import engine.model.Holding;
import engine.model.LmsrEvent;
import engine.model.Order;
import engine.model.OrderBook;
import engine.model.OrderBookEvent;
import engine.model.OrderSide;
import engine.model.Trade;
import engine.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GuessMarketEngine implements GMEngine {

    private Market market;
    private String loadedFilePath;

    // ---------- loading ----------

    @Override
    public void loadMarketFile(String path) {
        Market loaded = MarketFileLoader.load(path);
        this.market = loaded;
        this.loadedFilePath = path;
    }

    @Override
    public boolean isLoaded() {
        return market != null;
    }

    @Override
    public String getLoadedFilePath() {
        return loadedFilePath;
    }

    // ---------- events ----------

    @Override
    public List<EventDTO> getEvents(EventFilterDTO filter) {
        requireLoaded();
        List<EventDTO> result = new ArrayList<>();
        for (Event event : market.getEvents()) {
            if (matches(event, filter)) {
                result.add(toEventDTO(event));
            }
        }
        return result;
    }

    private boolean matches(Event event, EventFilterDTO filter) {
        if (filter == null) {
            return true;
        }
        if (filter.getOrderBook() != null
                && filter.getOrderBook() != (event instanceof OrderBookEvent)) {
            return false;
        }
        if (filter.getStatusLabel() != null
                && !filter.getStatusLabel().equals(event.getStatus().getLabel())) {
            return false;
        }
        return filter.getCommissionTypeLabel() == null
                || filter.getCommissionTypeLabel().equals(event.getCommissionType().getLabel());
    }

    @Override
    public EventDTO getEvent(int eventId) {
        requireLoaded();
        return toEventDTO(market.requireEvent(eventId));
    }

    @Override
    public LmsrEventStateDTO getLmsrEventState(int eventId) {
        requireLoaded();
        return toLmsrState(requireLmsr(market.requireEvent(eventId)));
    }

    @Override
    public OrderBookEventStateDTO getOrderBookEventState(int eventId) {
        requireLoaded();
        return toOrderBookState(requireOrderBook(market.requireEvent(eventId)));
    }

    // ---------- users ----------

    @Override
    public List<UserDTO> getUsers() {
        requireLoaded();
        List<UserDTO> result = new ArrayList<>();
        for (User user : market.getUsers()) {
            result.add(toUserDTO(user));
        }
        return result;
    }

    @Override
    public UserDTO getUser(String userName) {
        requireLoaded();
        return toUserDTO(market.requireUser(userName));
    }

    @Override
    public List<EventDTO> getUserEvents(String userName) {
        requireLoaded();
        // Every event, not only the ones already joined: a user has to be able
        // to reach an event in order to take part in it for the first time.
        market.requireUser(userName);
        return getEvents(EventFilterDTO.all());
    }

    @Override
    public boolean isParticipant(String userName, int eventId) {
        requireLoaded();
        return market.requireUser(userName).participatesIn(eventId);
    }

    @Override
    public UserEventInvolvementDTO getUserInvolvement(String userName, int eventId) {
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
    public List<Double> getUserBalanceHistory(String userName) {
        requireLoaded();
        return market.requireUser(userName).getBalanceHistory();
    }

    // ---------- lifecycle ----------

    @Override
    public void openEvent(int eventId, String actingUserName) {
        requireLoaded();
        Event event = market.requireEvent(eventId);
        event.open(market.requireUser(actingUserName));
    }

    @Override
    public CloseResultDTO closeEvent(int eventId, String actingUserName, int winningOptionIndex) {
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
    public PurchaseQuoteDTO quoteLmsrPurchase(int eventId, String userName, int optionIndex, long quantity) {
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
    public BuyResultDTO buyLmsrShares(int eventId, String userName, int optionIndex, long quantity) {
        requireLoaded();
        LmsrEvent event = requireLmsr(market.requireEvent(eventId));
        User buyer = market.requireUser(userName);
        User marketMaker = market.marketMakerOf(event);

        double[] result = event.buy(buyer, marketMaker, optionIndex, quantity);
        return new BuyResultDTO(result[0], result[1], result[2],
                buyer.getAccount().getBalance(), buyer.isBlocked(), toLmsrState(event));
    }

    @Override
    public OrderQuoteDTO quoteOrder(int eventId, String userName, int optionIndex,
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
                maxPrice, priceValid, opposingPrice, wouldTrade, availableQuantity);
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
    public OrderResultDTO placeOrder(int eventId, String userName, int optionIndex,
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
    public EventDTO createEvent(NewEventRequestDTO request, String creatorUserName) {
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
