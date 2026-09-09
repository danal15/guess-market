package engine.api.dto;

/**
 * Filter for the events screen. A null field means "all" for that dimension,
 * so one object expresses every combination the three filter groups can make.
 */
public final class EventFilterDTO {

    private final Boolean orderBook;
    private final String statusLabel;
    private final String commissionTypeLabel;

    public EventFilterDTO(Boolean orderBook, String statusLabel, String commissionTypeLabel) {
        this.orderBook = orderBook;
        this.statusLabel = statusLabel;
        this.commissionTypeLabel = commissionTypeLabel;
    }

    public static EventFilterDTO all() {
        return new EventFilterDTO(null, null, null);
    }

    /** Null means both methods. */
    public Boolean getOrderBook() {
        return orderBook;
    }

    /** Null means every status. */
    public String getStatusLabel() {
        return statusLabel;
    }

    /** Null means both commission types. */
    public String getCommissionTypeLabel() {
        return commissionTypeLabel;
    }
}
