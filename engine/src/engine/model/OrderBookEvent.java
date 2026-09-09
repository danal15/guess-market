package engine.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class OrderBookEvent extends Event {

    private static final long serialVersionUID = 1L;

    private final int d;
    private final boolean allowMint;
    private final int initialInvestment;
    private final OrderBook[] books;
    private final List<ExecutedTrade> executedTrades = new ArrayList<>();

    public OrderBookEvent(int id, String name, String description, int commissionPercent,
                          CommissionType commissionType, String mmUserName,
                          String option1Name, String option2Name,
                          int d, boolean allowMint, int initialInvestment) {
        super(id, name, description, commissionPercent, commissionType, mmUserName, option1Name, option2Name);
        this.d = d;
        this.allowMint = allowMint;
        this.initialInvestment = initialInvestment;
        this.books = new OrderBook[] { new OrderBook(0), new OrderBook(1) };
    }

    @Override
    public EventMethod getMethod() {
        return EventMethod.ORDER_BOOK;
    }

    /** Whole pairs the initial investment can buy; a partial pair is not created. */
    public long initialPairs() {
        return initialInvestment / d;
    }

    @Override
    public double requiredOpeningFunds() {
        return initialPairs() * (double) d;
    }

    @Override
    protected void fundOpening(User marketMaker) {
        long pairs = initialPairs();
        double cost = pairs * (double) d;
        if (cost > 0) {
            marketMaker.pay(cost);
            getAccount().deposit(cost);
        }
        if (pairs > 0) {
            Holding holding = marketMaker.holdingFor(getId());
            holding.markOrdered();
            // A pair costs d in total, so each side is credited with half of it.
            holding.addShares(0, pairs, cost / 2.0);
            holding.addShares(1, pairs, cost / 2.0);
            getOption(0).addShares(pairs);
            getOption(1).addShares(pairs);
            recordTrade(new ExecutedTrade(0, 0, marketMaker.getName(), null, pairs,
                    d / 2.0, ExecutedTrade.Kind.INITIAL_ALLOCATION));
        }
    }

    @Override
    protected CloseSummary resolve(int winningIndex, Collection<User> allUsers,
                                   User marketMaker, CloseSummary summary) {
        summary.recordCancelledOrders(
                books[0].getBids().size() + books[0].getAsks().size()
                        + books[1].getBids().size() + books[1].getAsks().size());
        books[0].cancelAll();
        books[1].cancelAll();

        for (User user : allUsers) {
            Holding holding = user.existingHolding(getId());
            if (holding == null) {
                continue;
            }
            long winningShares = holding.getQuantity(winningIndex);
            if (winningShares <= 0) {
                continue;
            }
            double gross = winningShares * (double) d;
            double fee = getCommissionType() == CommissionType.ON_CLOSE ? commissionOn(gross) : 0.0;
            getAccount().withdraw(gross);
            user.receive(gross - fee);
            holding.recordPayout(winningIndex, gross - fee);
            holding.addCommission(fee);
            creditCommission(marketMaker, fee);
            summary.recordPayout(gross - fee, fee);
        }

        double remainder = getAccount().getBalance();
        if (remainder != 0) {
            getAccount().withdraw(remainder);
            marketMaker.receive(remainder);
            summary.recordReturnedToMarketMaker(remainder);
        }
        return summary;
    }

    /** Brings a freshly minted pair of shares into existence. */
    public void creditMintedPair(long quantity) {
        getOption(0).addShares(quantity);
        getOption(1).addShares(quantity);
    }

    public void recordTrade(ExecutedTrade trade) {
        executedTrades.add(trade);
        if (trade.getKind() != ExecutedTrade.Kind.INITIAL_ALLOCATION) {
            books[trade.getOptionIndex()].setLastTradePrice(trade.getPrice());
        }
    }

    public OrderBook book(int optionIndex) {
        checkOptionIndex(optionIndex);
        return books[optionIndex];
    }

    public double maxPrice() {
        return d - 0.01;
    }

    public int getD() {
        return d;
    }

    public boolean isAllowMint() {
        return allowMint;
    }

    public int getInitialInvestment() {
        return initialInvestment;
    }

    public List<ExecutedTrade> getExecutedTrades() {
        return Collections.unmodifiableList(executedTrades);
    }
}
