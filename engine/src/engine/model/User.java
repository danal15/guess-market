package engine.model;

import engine.api.exception.TradingException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class User implements Serializable {

    /** Raised to 2 when the balance history became a list of movements. */
    private static final long serialVersionUID = 2L;

    private static final double NEGATIVE_TOLERANCE = 1e-9;

    private static final String OPENING_BALANCE = "Opening balance";
    private static final String MONEY_OUT = "Payment";
    private static final String MONEY_IN = "Receipt";
    private static final String DEPOSIT = "Funds loaded";

    private final String name;
    private final Account account;
    private final Map<Integer, Holding> holdings = new LinkedHashMap<>();
    private final Set<Integer> mmOfEventIds = new LinkedHashSet<>();
    private boolean blocked;

    /** Every movement of money, oldest first. The account screen and the chart both read it. */
    private final List<Movement> movements = new ArrayList<>();

    public User(String name, double initialCash) {
        this.name = name;
        this.account = new Account(initialCash);
        this.movements.add(new Movement(OPENING_BALANCE, null, initialCash, initialCash));
    }

    public void pay(double amount) {
        pay(amount, MONEY_OUT, null);
    }

    public void pay(double amount, String reason, String eventName) {
        account.withdraw(amount);
        movements.add(new Movement(reason, eventName, -amount, account.getBalance()));
        updateBlockedState();
    }

    public void receive(double amount) {
        receive(amount, MONEY_IN, null);
    }

    public void receive(double amount, String reason, String eventName) {
        account.deposit(amount);
        movements.add(new Movement(reason, eventName, amount, account.getBalance()));
    }

    /**
     * Money the user puts into their own account. Unlike every other way money
     * arrives, this one can lift a block: the account was frozen because it had
     * gone below zero, and once it is covered again there is nothing left to
     * freeze. Only a deliberate deposit does this, so a payout from an event
     * still leaves a blocked account blocked.
     */
    public void loadFunds(double amount) {
        if (amount <= 0) {
            throw new TradingException("The amount to load must be greater than zero.");
        }
        account.deposit(amount);
        movements.add(new Movement(DEPOSIT, null, amount, account.getBalance()));
        if (account.getBalance() >= -NEGATIVE_TOLERANCE) {
            blocked = false;
        }
    }

    public List<Movement> getMovements() {
        return Collections.unmodifiableList(movements);
    }

    /** Balance after every money movement, oldest first; drives the balance chart. */
    public List<Double> getBalanceHistory() {
        List<Double> balances = new ArrayList<>(movements.size());
        for (Movement movement : movements) {
            balances.add(movement.getBalanceAfter());
        }
        return balances;
    }

    public boolean canAfford(double amount) {
        return account.getBalance() + NEGATIVE_TOLERANCE >= amount;
    }

    public void requireActive() {
        if (blocked) {
            throw new TradingException("User '" + name
                    + "' is blocked (account went negative) and cannot perform any further actions."
                    + " Load funds into the account to lift the block.");
        }
    }

    public Holding holdingFor(int eventId) {
        return holdings.computeIfAbsent(eventId, Holding::new);
    }

    public Holding existingHolding(int eventId) {
        return holdings.get(eventId);
    }

    public boolean participatesIn(int eventId) {
        return holdings.containsKey(eventId);
    }

    public void addMarketMakerEvent(int eventId) {
        mmOfEventIds.add(eventId);
    }

    public boolean isMarketMakerOf(int eventId) {
        return mmOfEventIds.contains(eventId);
    }

    public boolean isMarketMaker() {
        return !mmOfEventIds.isEmpty();
    }

    public Set<Integer> getMarketMakerEventIds() {
        return Collections.unmodifiableSet(mmOfEventIds);
    }

    public Set<Integer> getParticipatingEventIds() {
        return Collections.unmodifiableSet(holdings.keySet());
    }

    public String getName() {
        return name;
    }

    public Account getAccount() {
        return account;
    }

    public boolean isBlocked() {
        return blocked;
    }

    private void updateBlockedState() {
        if (account.getBalance() < -NEGATIVE_TOLERANCE) {
            blocked = true;
        }
    }
}
