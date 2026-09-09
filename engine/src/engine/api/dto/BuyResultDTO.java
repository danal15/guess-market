package engine.api.dto;

public final class BuyResultDTO {

    private final double sharesCost;
    private final double commissionPaid;
    private final double totalPaid;
    private final double newBalance;
    private final boolean blockedNow;
    private final LmsrEventStateDTO afterState;

    public BuyResultDTO(double sharesCost, double commissionPaid, double totalPaid,
                        double newBalance, boolean blockedNow, LmsrEventStateDTO afterState) {
        this.sharesCost = sharesCost;
        this.commissionPaid = commissionPaid;
        this.totalPaid = totalPaid;
        this.newBalance = newBalance;
        this.blockedNow = blockedNow;
        this.afterState = afterState;
    }

    public double getSharesCost() {
        return sharesCost;
    }

    public double getCommissionPaid() {
        return commissionPaid;
    }

    public double getTotalPaid() {
        return totalPaid;
    }

    public double getNewBalance() {
        return newBalance;
    }

    public boolean isBlockedNow() {
        return blockedNow;
    }

    public LmsrEventStateDTO getAfterState() {
        return afterState;
    }
}
