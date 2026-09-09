package engine.model;

import engine.api.exception.TradingException;
import engine.core.lmsr.LmsrMath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LmsrEvent extends Event {

    private static final long serialVersionUID = 1L;

    /** Every winning LMSR share pays exactly one dollar (appendix A). */
    private static final double PAYOUT_PER_SHARE = 1.0;

    private final int b;
    private final List<Trade> trades = new ArrayList<>();
    /** Both option prices after every purchase, oldest first; drives the price chart. */
    private final List<double[]> priceHistory = new ArrayList<>();

    public LmsrEvent(int id, String name, String description, int commissionPercent,
                     CommissionType commissionType, String mmUserName,
                     String option1Name, String option2Name, int b) {
        super(id, name, description, commissionPercent, commissionType, mmUserName, option1Name, option2Name);
        this.b = b;
    }

    @Override
    public EventMethod getMethod() {
        return EventMethod.LMSR;
    }

    @Override
    public double requiredOpeningFunds() {
        return LmsrMath.seed(b);
    }

    @Override
    protected void fundOpening(User marketMaker) {
        double subsidy = requiredOpeningFunds();
        marketMaker.pay(subsidy);
        getAccount().deposit(subsidy);
        Holding holding = marketMaker.holdingFor(getId());
        holding.markOrdered();
        holding.recordMarketMakerPaid(subsidy);
    }

    public double priceOf(int optionIndex) {
        checkOptionIndex(optionIndex);
        int other = 1 - optionIndex;
        return LmsrMath.price(getOption(optionIndex).getSharesOutstanding(),
                getOption(other).getSharesOutstanding(), b);
    }

    /**
     * Works out what a purchase would cost without changing anything, so the
     * screen can show the price before the user commits to it.
     * Returns {sharesCost, commission, totalPaid, priceAfterwards}.
     */
    public double[] quote(int optionIndex, long quantity) {
        checkOptionIndex(optionIndex);
        if (quantity < 1) {
            throw new TradingException("Quantity must be at least 1.");
        }
        long qFirst = getOption(0).getSharesOutstanding();
        long qSecond = getOption(1).getSharesOutstanding();
        double before = LmsrMath.cost(qFirst, qSecond, b);
        long afterFirst = optionIndex == 0 ? qFirst + quantity : qFirst;
        long afterSecond = optionIndex == 1 ? qSecond + quantity : qSecond;

        double sharesCost = LmsrMath.cost(afterFirst, afterSecond, b) - before;
        double commission = getCommissionType() == CommissionType.ON_PURCHASE ? commissionOn(sharesCost) : 0.0;
        double priceAfter = optionIndex == 0
                ? LmsrMath.price(afterFirst, afterSecond, b)
                : LmsrMath.price(afterSecond, afterFirst, b);

        return new double[] { sharesCost, commission, sharesCost + commission, priceAfter };
    }

    /** Buys shares against the event account; returns {sharesCost, commission, totalPaid}. */
    public double[] buy(User buyer, User marketMaker, int optionIndex, long quantity) {
        checkOptionIndex(optionIndex);
        requireActiveForTrading();
        buyer.requireActive();
        if (quantity < 1) {
            throw new TradingException("Quantity must be at least 1.");
        }

        long qFirst = getOption(0).getSharesOutstanding();
        long qSecond = getOption(1).getSharesOutstanding();
        double before = LmsrMath.cost(qFirst, qSecond, b);
        long afterFirst = optionIndex == 0 ? qFirst + quantity : qFirst;
        long afterSecond = optionIndex == 1 ? qSecond + quantity : qSecond;
        double sharesCost = LmsrMath.cost(afterFirst, afterSecond, b) - before;

        double commission = getCommissionType() == CommissionType.ON_PURCHASE ? commissionOn(sharesCost) : 0.0;
        double totalPaid = sharesCost + commission;

        if (!buyer.canAfford(totalPaid)) {
            throw new TradingException(String.format(
                    "%s cannot afford this purchase: it costs %.2f but the account holds only %.2f.",
                    buyer.getName(), totalPaid, buyer.getAccount().getBalance()));
        }

        buyer.pay(totalPaid);
        getAccount().deposit(sharesCost);
        creditCommission(marketMaker, commission);
        getOption(optionIndex).addShares(quantity);

        Holding holding = buyer.holdingFor(getId());
        holding.markOrdered();
        holding.addShares(optionIndex, quantity, sharesCost);
        holding.addCommission(commission);

        trades.add(new Trade(buyer.getName(), getOption(optionIndex).getName(), quantity, sharesCost, commission));
        priceHistory.add(new double[] { priceOf(0), priceOf(1) });

        return new double[] { sharesCost, commission, totalPaid };
    }

    @Override
    protected CloseSummary resolve(int winningIndex, Collection<User> allUsers,
                                   User marketMaker, CloseSummary summary) {
        for (User user : allUsers) {
            Holding holding = user.existingHolding(getId());
            if (holding == null) {
                continue;
            }
            long winningShares = holding.getQuantity(winningIndex);
            if (winningShares <= 0) {
                continue;
            }
            double gross = winningShares * PAYOUT_PER_SHARE;
            double fee = getCommissionType() == CommissionType.ON_CLOSE ? commissionOn(gross) : 0.0;
            getAccount().withdraw(gross);
            user.receive(gross - fee);
            holding.recordPayout(winningIndex, gross);
            holding.addCommission(fee);
            creditCommission(marketMaker, fee);
            summary.recordPayout(gross - fee, fee);
        }

        double remainder = getAccount().getBalance();
        if (remainder > 0) {
            getAccount().withdraw(remainder);
            marketMaker.receive(remainder);
            marketMaker.holdingFor(getId()).recordMarketMakerReceived(remainder);
            summary.recordReturnedToMarketMaker(remainder);
        }
        return summary;
    }

    public int getB() {
        return b;
    }

    public List<Trade> getTrades() {
        return Collections.unmodifiableList(trades);
    }

    public List<double[]> getPriceHistory() {
        return Collections.unmodifiableList(priceHistory);
    }

    public List<Trade> getTradesOf(String userName) {
        List<Trade> result = new ArrayList<>();
        for (Trade trade : trades) {
            if (trade.getUserName().equals(userName)) {
                result.add(trade);
            }
        }
        return result;
    }
}
