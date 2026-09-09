package engine.api.dto;

public final class OrderBookStatsDTO {

    private final Double lastTradePrice;
    private final Double bestBid;
    private final Double bestAsk;
    private final Double midPrice;
    private final Double spread;

    public OrderBookStatsDTO(Double lastTradePrice, Double bestBid, Double bestAsk, Double midPrice, Double spread) {
        this.lastTradePrice = lastTradePrice;
        this.bestBid = bestBid;
        this.bestAsk = bestAsk;
        this.midPrice = midPrice;
        this.spread = spread;
    }

    public Double getLastTradePrice() {
        return lastTradePrice;
    }

    public Double getBestBid() {
        return bestBid;
    }

    public Double getBestAsk() {
        return bestAsk;
    }

    public Double getMidPrice() {
        return midPrice;
    }

    public Double getSpread() {
        return spread;
    }
}
