package engine.api.dto;

public final class TradeDTO {

    private final String userName;
    private final String optionName;
    private final long quantity;
    private final double sharesCost;
    private final double commissionPaid;

    public TradeDTO(String userName, String optionName, long quantity, double sharesCost, double commissionPaid) {
        this.userName = userName;
        this.optionName = optionName;
        this.quantity = quantity;
        this.sharesCost = sharesCost;
        this.commissionPaid = commissionPaid;
    }

    public String getUserName() {
        return userName;
    }

    public String getOptionName() {
        return optionName;
    }

    public long getQuantity() {
        return quantity;
    }

    public double getSharesCost() {
        return sharesCost;
    }

    public double getCommissionPaid() {
        return commissionPaid;
    }
}
