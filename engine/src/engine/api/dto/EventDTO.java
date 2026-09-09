package engine.api.dto;

public final class EventDTO {

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercent;
    private final String commissionTypeLabel;
    private final String methodLabel;
    private final boolean orderBook;
    private final String statusLabel;
    private final String marketMakerName;
    private final String option1Name;
    private final String option2Name;
    private final double accountBalance;

    public EventDTO(int id, String name, String description, int commissionPercent, String commissionTypeLabel, String methodLabel, boolean orderBook, String statusLabel, String marketMakerName, String option1Name, String option2Name, double accountBalance) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionTypeLabel = commissionTypeLabel;
        this.methodLabel = methodLabel;
        this.orderBook = orderBook;
        this.statusLabel = statusLabel;
        this.marketMakerName = marketMakerName;
        this.option1Name = option1Name;
        this.option2Name = option2Name;
        this.accountBalance = accountBalance;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCommissionPercent() {
        return commissionPercent;
    }

    public String getCommissionTypeLabel() {
        return commissionTypeLabel;
    }

    public String getMethodLabel() {
        return methodLabel;
    }

    public boolean isOrderBook() {
        return orderBook;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getMarketMakerName() {
        return marketMakerName;
    }

    public String getOption1Name() {
        return option1Name;
    }

    public String getOption2Name() {
        return option2Name;
    }

    public double getAccountBalance() {
        return accountBalance;
    }
}
