package engine.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resting bids and asks for a single option, plus that option's last traded
 * price. Both sides are kept sorted by price then by arrival sequence, so the
 * best order is always first and ties are broken in favour of whoever was
 * waiting longer.
 */
public class OrderBook implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Comparator<Order> BID_ORDER =
            Comparator.comparingDouble(Order::getPrice).reversed().thenComparingLong(Order::getSequence);
    private static final Comparator<Order> ASK_ORDER =
            Comparator.comparingDouble(Order::getPrice).thenComparingLong(Order::getSequence);

    private final int optionIndex;
    private final List<Order> bids = new ArrayList<>();
    private final List<Order> asks = new ArrayList<>();
    private Double lastTradePrice;

    public OrderBook(int optionIndex) {
        this.optionIndex = optionIndex;
    }

    public void add(Order order) {
        if (order.getSide() == OrderSide.BUY) {
            bids.add(order);
            bids.sort(BID_ORDER);
        } else {
            asks.add(order);
            asks.sort(ASK_ORDER);
        }
    }

    public void remove(Order order) {
        bids.remove(order);
        asks.remove(order);
    }

    public void removeExhausted() {
        bids.removeIf(Order::isExhausted);
        asks.removeIf(Order::isExhausted);
    }

    public Order bestBid() {
        return bids.isEmpty() ? null : bids.get(0);
    }

    public Order bestAsk() {
        return asks.isEmpty() ? null : asks.get(0);
    }

    public List<Order> getBids() {
        return new ArrayList<>(bids);
    }

    public List<Order> getAsks() {
        return new ArrayList<>(asks);
    }

    public void cancelAll() {
        bids.clear();
        asks.clear();
    }

    public void setLastTradePrice(double price) {
        this.lastTradePrice = price;
    }

    public Double getLastTradePrice() {
        return lastTradePrice;
    }

    public Double getBestBidPrice() {
        Order order = bestBid();
        return order == null ? null : order.getPrice();
    }

    public Double getBestAskPrice() {
        Order order = bestAsk();
        return order == null ? null : order.getPrice();
    }

    /** Average of best bid and best ask; undefined unless both sides are quoted. */
    public Double getMidPrice() {
        Double bid = getBestBidPrice();
        Double ask = getBestAskPrice();
        return (bid == null || ask == null) ? null : (bid + ask) / 2.0;
    }

    /** Distance between best ask and best bid; undefined unless both sides are quoted. */
    public Double getSpread() {
        Double bid = getBestBidPrice();
        Double ask = getBestAskPrice();
        return (bid == null || ask == null) ? null : ask - bid;
    }

    /**
     * The price used to value a holding: the last trade if there was one,
     * otherwise the mid price, otherwise half the base value.
     */
    public double referencePrice(int d) {
        if (lastTradePrice != null) {
            return lastTradePrice;
        }
        Double mid = getMidPrice();
        return mid != null ? mid : d / 2.0;
    }

    public int getOptionIndex() {
        return optionIndex;
    }
}
