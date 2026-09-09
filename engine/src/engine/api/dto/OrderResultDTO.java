package engine.api.dto;

import java.util.List;

public final class OrderResultDTO {

    private final List<FillDTO> fills;
    private final long mintedQuantity;
    private final long restingQuantity;
    private final double cashPaid;
    private final double cashReceived;
    private final double commissionPaid;
    private final double newBalance;
    private final boolean blockedNow;
    private final OrderBookEventStateDTO afterState;

    public OrderResultDTO(List<FillDTO> fills, long mintedQuantity, long restingQuantity,
                          double cashPaid, double cashReceived, double commissionPaid,
                          double newBalance, boolean blockedNow, OrderBookEventStateDTO afterState) {
        this.fills = fills;
        this.mintedQuantity = mintedQuantity;
        this.restingQuantity = restingQuantity;
        this.cashPaid = cashPaid;
        this.cashReceived = cashReceived;
        this.commissionPaid = commissionPaid;
        this.newBalance = newBalance;
        this.blockedNow = blockedNow;
        this.afterState = afterState;
    }

    public List<FillDTO> getFills() {
        return fills;
    }

    public long getMintedQuantity() {
        return mintedQuantity;
    }

    public long getRestingQuantity() {
        return restingQuantity;
    }

    public double getCashPaid() {
        return cashPaid;
    }

    public double getCashReceived() {
        return cashReceived;
    }

    public double getCommissionPaid() {
        return commissionPaid;
    }

    public double getNewBalance() {
        return newBalance;
    }

    public boolean isBlockedNow() {
        return blockedNow;
    }

    public OrderBookEventStateDTO getAfterState() {
        return afterState;
    }
}
