package engine.model;

import engine.api.exception.TradingException;

import java.io.Serializable;
import java.util.Collection;

/**
 * Base of every market event. Holds the identity, configuration, status,
 * account and market maker that both trading methods share, and owns the
 * open/close protocol. The method specific state and money movement live in
 * the subclasses.
 */
public abstract class Event implements Serializable {

    private static final long serialVersionUID = 2L;

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercent;
    private final CommissionType commissionType;
    private final String mmUserName;
    private final Option[] options;
    private final Account account;
    private double totalCommissionCollected;
    private EventStatus status;
    private Integer winningOptionIndex;

    protected Event(int id, String name, String description, int commissionPercent,
                    CommissionType commissionType, String mmUserName,
                    String option1Name, String option2Name) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.mmUserName = mmUserName;
        this.options = new Option[] { new Option(option1Name), new Option(option2Name) };
        this.account = new Account();
        this.totalCommissionCollected = 0.0;
        this.status = EventStatus.NOT_STARTED;
        this.winningOptionIndex = null;
    }

    public abstract EventMethod getMethod();

    /** How much the market maker must have in hand to open this event. */
    public abstract double requiredOpeningFunds();

    /** Moves the market maker's money into the event; called only by open(). */
    protected abstract void fundOpening(User marketMaker);

    /** Pays out the winners; called only by close(). */
    protected abstract CloseSummary resolve(int winningIndex, Collection<User> allUsers,
                                            User marketMaker, CloseSummary summary);

    public void open(User marketMaker) {
        requireMarketMaker(marketMaker);
        marketMaker.requireActive();
        if (status != EventStatus.NOT_STARTED) {
            throw new TradingException("Event '" + name + "' has already been started.");
        }
        double needed = requiredOpeningFunds();
        if (!marketMaker.canAfford(needed)) {
            throw new TradingException(String.format(
                    "%s cannot open '%s': it requires %.2f but the account holds only %.2f.",
                    marketMaker.getName(), name, needed, marketMaker.getAccount().getBalance()));
        }
        fundOpening(marketMaker);
        status = EventStatus.ACTIVE;
    }

    public CloseSummary close(User marketMaker, int winningIndex, Collection<User> allUsers) {
        requireMarketMaker(marketMaker);
        // Closing pays out every winner and cannot be undone, so it is held to
        // the same standard as every other action a blocked user may not take.
        marketMaker.requireActive();
        checkOptionIndex(winningIndex);
        if (status != EventStatus.ACTIVE) {
            throw new TradingException("Event '" + name + "' is not active, so it cannot be closed.");
        }
        CloseSummary summary = new CloseSummary(getOption(winningIndex).getName());
        resolve(winningIndex, allUsers, marketMaker, summary);
        winningOptionIndex = winningIndex;
        status = EventStatus.CLOSED;
        return summary;
    }

    public void requireActiveForTrading() {
        if (status != EventStatus.ACTIVE) {
            throw new TradingException("Event '" + name + "' is " + status.getLabel().toLowerCase()
                    + ", so no trading is possible.");
        }
    }

    public boolean isMarketMaker(String userName) {
        return mmUserName.equals(userName);
    }

    protected void requireMarketMaker(User user) {
        if (!isMarketMaker(user.getName())) {
            throw new TradingException("Only " + mmUserName + ", the market maker of '" + name
                    + "', can perform this action.");
        }
    }

    /**
     * Charges the on-purchase commission to a buyer and hands it to the market
     * maker. Does nothing when the event collects its commission on close.
     * Returns the amount actually charged.
     */
    public double applyPurchaseCommission(User buyer, User marketMaker, Holding buyerHolding, double tradeValue) {
        if (getCommissionType() != CommissionType.ON_PURCHASE) {
            return 0.0;
        }
        double fee = commissionOn(tradeValue);
        if (fee <= 0) {
            return 0.0;
        }
        buyer.pay(fee);
        buyerHolding.addCommission(fee);
        creditCommission(marketMaker, fee);
        return fee;
    }

    /** Credits commission to the market maker and records it on the event. */
    protected void creditCommission(User marketMaker, double amount) {
        if (amount <= 0) {
            return;
        }
        marketMaker.receive(amount);
        totalCommissionCollected += amount;
    }

    protected double commissionOn(double amount) {
        return amount * commissionPercent / 100.0;
    }

    protected void checkOptionIndex(int optionIndex) {
        if (optionIndex != 0 && optionIndex != 1) {
            throw new IllegalArgumentException("Option index must be 0 or 1, got: " + optionIndex);
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCommissionPercent() {
        return commissionPercent;
    }

    public CommissionType getCommissionType() {
        return commissionType;
    }

    public String getMarketMakerName() {
        return mmUserName;
    }

    public Option getOption(int index) {
        checkOptionIndex(index);
        return options[index];
    }

    public Account getAccount() {
        return account;
    }

    public double getTotalCommissionCollected() {
        return totalCommissionCollected;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Integer getWinningOptionIndex() {
        return winningOptionIndex;
    }
}
