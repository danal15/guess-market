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

    private static final long serialVersionUID = 1L;

    private static final double NEGATIVE_TOLERANCE = 1e-9;

    private final String name;
    private final Account account;
    private final Map<Integer, Holding> holdings = new LinkedHashMap<>();
    private final Set<Integer> mmOfEventIds = new LinkedHashSet<>();
    private boolean blocked;

    private final List<Double> balanceHistory = new ArrayList<>();

    public User(String name, double initialCash) {
        this.name = name;
        this.account = new Account(initialCash);
        this.balanceHistory.add(initialCash);
    }

    public void pay(double amount) {
        account.withdraw(amount);
        balanceHistory.add(account.getBalance());
        updateBlockedState();
    }

    public void receive(double amount) {
        account.deposit(amount);
        balanceHistory.add(account.getBalance());
    }

    /** Balance after every money movement, oldest first; drives the balance chart. */
    public List<Double> getBalanceHistory() {
        return Collections.unmodifiableList(balanceHistory);
    }

    public boolean canAfford(double amount) {
        return account.getBalance() + NEGATIVE_TOLERANCE >= amount;
    }

    public void requireActive() {
        if (blocked) {
            throw new TradingException("User '" + name
                    + "' is blocked (account went negative) and cannot perform any further actions.");
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
