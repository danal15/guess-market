package engine.model;

import java.io.Serializable;

/**
 * One movement of money in and out of a user's account, kept so the account
 * can be shown as a list of lines rather than a single number. The balance
 * after the movement is stored rather than recomputed, so a line always says
 * what the account really held at that moment.
 */
public class Movement implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String reason;
    private final String eventName;
    private final double amount;
    private final double balanceAfter;

    /**
     * @param amount    positive when money came in, negative when it went out
     * @param eventName the event the money moved because of, or null when the
     *                  movement has nothing to do with one
     */
    public Movement(String reason, String eventName, double amount, double balanceAfter) {
        this.reason = reason;
        this.eventName = eventName;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public String getEventName() {
        return eventName;
    }

    public double getAmount() {
        return amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }
}
