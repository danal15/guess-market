package engine.core.ob;

import engine.api.exception.TradingException;
import engine.core.Market;
import engine.model.CommissionType;
import engine.model.ExecutedTrade;
import engine.model.Holding;
import engine.model.Order;
import engine.model.OrderBook;
import engine.model.OrderBookEvent;
import engine.model.OrderSide;
import engine.model.User;

/**
 * Executes one incoming order against an order book event: first by matching
 * it against resting orders on the same option, then - when the event allows
 * it - by minting new pairs against a buyer of the opposite option, and
 * finally by resting whatever is left.
 */
public class OrderMatcher {

    private static final double EPSILON = 1e-9;

    private final Market market;
    private final OrderBookEvent event;
    private final User trader;
    private final User marketMaker;
    private final OrderOutcome outcome = new OrderOutcome();

    public OrderMatcher(Market market, OrderBookEvent event, User trader, User marketMaker) {
        this.market = market;
        this.event = event;
        this.trader = trader;
        this.marketMaker = marketMaker;
    }

    public OrderOutcome submit(int optionIndex, OrderSide side, double price, long quantity) {
        validate(optionIndex, side, price, quantity);

        Order order = new Order(market.nextSequence(), trader.getName(), optionIndex, side, price, quantity);
        trader.holdingFor(event.getId()).markOrdered();

        matchAgainstBook(order);
        if (order.getRemainingQuantity() > 0 && side == OrderSide.BUY && event.isAllowMint()) {
            mintAgainstOppositeBook(order);
        }
        if (order.getRemainingQuantity() > 0) {
            event.book(optionIndex).add(order);
            outcome.setRestingQuantity(order.getRemainingQuantity());
        }

        outcome.setTraderBlocked(trader.isBlocked());
        return outcome;
    }

    private void validate(int optionIndex, OrderSide side, double price, long quantity) {
        event.requireActiveForTrading();
        trader.requireActive();

        if (optionIndex != 0 && optionIndex != 1) {
            throw new TradingException("Please choose one of the two options.");
        }
        if (quantity < 1) {
            throw new TradingException("Quantity must be at least 1.");
        }
        if (Math.abs(price * 100 - Math.round(price * 100)) > 1e-6) {
            throw new TradingException("Price must be given in whole cents, for example 0.42.");
        }
        double max = event.maxPrice();
        if (price < 0.01 - EPSILON || price > max + EPSILON) {
            throw new TradingException(String.format(
                    "Price must be between 0.01 and %.2f (the base value is %d).", max, event.getD()));
        }

        if (side == OrderSide.SELL) {
            Holding holding = trader.existingHolding(event.getId());
            long held = holding == null ? 0 : holding.getQuantity(optionIndex);
            if (held < quantity) {
                throw new TradingException(String.format(
                        "%s holds only %d shares of '%s' and cannot sell %d.",
                        trader.getName(), held, event.getOption(optionIndex).getName(), quantity));
            }
        } else {
            // The commission is part of what a buyer pays, so it has to be part
            // of the check too - otherwise an order can be accepted here and
            // still take the account below zero when it fills.
            double value = quantity * price;
            double commission = event.getCommissionType() == CommissionType.ON_PURCHASE
                    ? value * event.getCommissionPercent() / 100.0
                    : 0.0;
            double needed = value + commission;
            if (!trader.canAfford(needed)) {
                throw new TradingException(String.format(
                        "%s cannot afford this order: it needs up to %.2f (%.2f plus %.2f commission)"
                                + " but the account holds only %.2f.",
                        trader.getName(), needed, value, commission, trader.getAccount().getBalance()));
            }
        }
    }

    private void matchAgainstBook(Order order) {
        OrderBook book = event.book(order.getOptionIndex());
        while (order.getRemainingQuantity() > 0) {
            Order resting = order.getSide() == OrderSide.BUY ? book.bestAsk() : book.bestBid();
            if (resting == null || !crosses(order, resting)) {
                return;
            }
            long quantity = Math.min(order.getRemainingQuantity(), resting.getRemainingQuantity());
            executeResale(order, resting, quantity, resting.getPrice());
            order.reduce(quantity);
            resting.reduce(quantity);
            if (resting.isExhausted()) {
                book.remove(resting);
            }
        }
    }

    private boolean crosses(Order incoming, Order resting) {
        return incoming.getSide() == OrderSide.BUY
                ? resting.getPrice() <= incoming.getPrice() + EPSILON
                : resting.getPrice() >= incoming.getPrice() - EPSILON;
    }

    private void executeResale(Order incoming, Order resting, long quantity, double price) {
        User counterparty = market.requireUser(resting.getUserName());
        boolean incomingIsBuyer = incoming.getSide() == OrderSide.BUY;
        User buyer = incomingIsBuyer ? trader : counterparty;
        User seller = incomingIsBuyer ? counterparty : trader;

        int optionIndex = incoming.getOptionIndex();
        double amount = quantity * price;

        Holding buyerHolding = buyer.holdingFor(event.getId());
        Holding sellerHolding = seller.holdingFor(event.getId());

        seller.receive(amount);
        sellerHolding.removeShares(optionIndex, quantity, amount);

        buyer.pay(amount);
        buyerHolding.addShares(optionIndex, quantity, amount);
        double fee = event.applyPurchaseCommission(buyer, marketMaker, buyerHolding, amount);

        event.recordTrade(new ExecutedTrade(market.nextSequence(), optionIndex,
                buyer.getName(), seller.getName(), quantity, price, ExecutedTrade.Kind.RESALE));

        outcome.addFill(new OrderOutcome.Fill(quantity, price, counterparty.getName(),
                ExecutedTrade.Kind.RESALE.getLabel()));
        if (incomingIsBuyer) {
            outcome.addCashPaid(amount);
            outcome.addCommissionPaid(fee);
        } else {
            outcome.addCashReceived(amount);
        }
    }

    /**
     * Two buyers on opposite options whose prices together reach the base value
     * can bring brand new shares into existence. The order that was already
     * resting keeps its own price; the incoming order pays the complement.
     */
    private void mintAgainstOppositeBook(Order order) {
        int otherIndex = 1 - order.getOptionIndex();
        OrderBook otherBook = event.book(otherIndex);
        int d = event.getD();

        while (order.getRemainingQuantity() > 0) {
            Order restingBid = otherBook.bestBid();
            if (restingBid == null || order.getPrice() + restingBid.getPrice() < d - EPSILON) {
                return;
            }
            long quantity = Math.min(order.getRemainingQuantity(), restingBid.getRemainingQuantity());
            executeMint(order, restingBid, quantity, d - restingBid.getPrice(), restingBid.getPrice());
            order.reduce(quantity);
            restingBid.reduce(quantity);
            if (restingBid.isExhausted()) {
                otherBook.remove(restingBid);
            }
        }
    }

    private void executeMint(Order incoming, Order resting, long quantity,
                             double incomingPrice, double restingPrice) {
        User restingUser = market.requireUser(resting.getUserName());
        double incomingAmount = quantity * incomingPrice;
        double restingAmount = quantity * restingPrice;

        Holding incomingHolding = trader.holdingFor(event.getId());
        Holding restingHolding = restingUser.holdingFor(event.getId());

        trader.pay(incomingAmount);
        restingUser.pay(restingAmount);
        event.getAccount().deposit(incomingAmount + restingAmount);

        event.creditMintedPair(quantity);
        incomingHolding.addShares(incoming.getOptionIndex(), quantity, incomingAmount);
        restingHolding.addShares(resting.getOptionIndex(), quantity, restingAmount);

        double incomingFee = event.applyPurchaseCommission(trader, marketMaker, incomingHolding, incomingAmount);
        event.applyPurchaseCommission(restingUser, marketMaker, restingHolding, restingAmount);

        event.recordTrade(new ExecutedTrade(market.nextSequence(), incoming.getOptionIndex(),
                trader.getName(), null, quantity, incomingPrice, ExecutedTrade.Kind.MINT));
        event.recordTrade(new ExecutedTrade(market.nextSequence(), resting.getOptionIndex(),
                restingUser.getName(), null, quantity, restingPrice, ExecutedTrade.Kind.MINT));

        outcome.addFill(new OrderOutcome.Fill(quantity, incomingPrice, null, ExecutedTrade.Kind.MINT.getLabel()));
        outcome.addMinted(quantity);
        outcome.addCashPaid(incomingAmount);
        outcome.addCommissionPaid(incomingFee);
    }
}
