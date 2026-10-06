package engine.api.dto;

/** One line of a user's account: what moved, why, and what was left after it. */
public final class MovementDTO {

    private final int index;
    private final String reason;
    private final String eventName;
    private final double amount;
    private final double balanceAfter;

    public MovementDTO(int index, String reason, String eventName, double amount, double balanceAfter) {
        this.index = index;
        this.reason = reason;
        this.eventName = eventName;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    /** Counted from 1, so the account reads like a statement. */
    public int getIndex() {
        return index;
    }

    public String getReason() {
        return reason;
    }

    /** The event the money moved because of, or null when there was none. */
    public String getEventName() {
        return eventName;
    }

    /** Positive when money came in, negative when it went out. */
    public double getAmount() {
        return amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }
}
