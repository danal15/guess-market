package engine.api.dto;

import java.util.List;

/** One option's book: its resting bids, its resting asks and its statistics. */
public final class OrderBookSideDTO {

    private final String optionName;
    private final List<OrderDTO> bids;
    private final List<OrderDTO> asks;
    private final OrderBookStatsDTO stats;
    private final long sharesOutstanding;

    public OrderBookSideDTO(String optionName, List<OrderDTO> bids, List<OrderDTO> asks,
                            OrderBookStatsDTO stats, long sharesOutstanding) {
        this.optionName = optionName;
        this.bids = bids;
        this.asks = asks;
        this.stats = stats;
        this.sharesOutstanding = sharesOutstanding;
    }

    public String getOptionName() {
        return optionName;
    }

    public List<OrderDTO> getBids() {
        return bids;
    }

    public List<OrderDTO> getAsks() {
        return asks;
    }

    public OrderBookStatsDTO getStats() {
        return stats;
    }

    public long getSharesOutstanding() {
        return sharesOutstanding;
    }
}
