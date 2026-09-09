package engine.api.dto;

public final class ParticipantDTO {

    private final String userName;
    private final long option1Quantity;
    private final double option1Value;
    private final long option2Quantity;
    private final double option2Value;
    private final boolean hasRestingOrders;

    public ParticipantDTO(String userName, long option1Quantity, double option1Value, long option2Quantity, double option2Value, boolean hasRestingOrders) {
        this.userName = userName;
        this.option1Quantity = option1Quantity;
        this.option1Value = option1Value;
        this.option2Quantity = option2Quantity;
        this.option2Value = option2Value;
        this.hasRestingOrders = hasRestingOrders;
    }

    public String getUserName() {
        return userName;
    }

    public long getOption1Quantity() {
        return option1Quantity;
    }

    public double getOption1Value() {
        return option1Value;
    }

    public long getOption2Quantity() {
        return option2Quantity;
    }

    public double getOption2Value() {
        return option2Value;
    }

    public boolean isHasRestingOrders() {
        return hasRestingOrders;
    }
}
