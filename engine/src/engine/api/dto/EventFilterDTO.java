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

    /**
     * Whether an event belongs on a screen showing this filter.
     *
     * The rule lives here so that there is exactly one of it. The engine filters
     * with it when it is asked for a list, and the exercise 3 client filters with
     * it again locally, because its screens are fed the whole market in one poll
     * and narrow it down without going back to the server.
     */
    public boolean matches(EventDTO event) {
        if (orderBook != null && event.isOrderBook() != orderBook) {
            return false;
        }
        if (statusLabel != null && !statusLabel.equals(event.getStatusLabel())) {
            return false;
        }
        return commissionTypeLabel == null
                || commissionTypeLabel.equals(event.getCommissionTypeLabel());
    }
}
