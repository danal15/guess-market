package engine.api.dto;

/** Everything needed to create an event from scratch (bonus 4). */
public final class NewEventRequestDTO {

    private final String name;
    private final String description;
    private final int commissionPercent;
    private final String commissionTypeLabel;
    private final String option1Name;
    private final String option2Name;
    private final boolean orderBook;
    private final int b;
    private final int baseValue;
    private final int initialInvestment;
    private final boolean allowMint;

    public NewEventRequestDTO(String name, String description, int commissionPercent,
                              String commissionTypeLabel, String option1Name, String option2Name,
                              boolean orderBook, int b, int baseValue, int initialInvestment,
                              boolean allowMint) {
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionTypeLabel = commissionTypeLabel;
        this.option1Name = option1Name;
        this.option2Name = option2Name;
        this.orderBook = orderBook;
        this.b = b;
        this.baseValue = baseValue;
        this.initialInvestment = initialInvestment;
        this.allowMint = allowMint;
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

    public String getOption1Name() {
        return option1Name;
    }

    public String getOption2Name() {
        return option2Name;
    }

    public boolean isOrderBook() {
        return orderBook;
    }

    public int getB() {
        return b;
    }

    public int getBaseValue() {
        return baseValue;
    }

    public int getInitialInvestment() {
        return initialInvestment;
    }

    public boolean isAllowMint() {
        return allowMint;
    }
}
