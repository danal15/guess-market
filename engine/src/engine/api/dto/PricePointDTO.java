package engine.api.dto;

public final class PricePointDTO {

    private final int index;
    private final double option1Price;
    private final double option2Price;

    public PricePointDTO(int index, double option1Price, double option2Price) {
        this.index = index;
        this.option1Price = option1Price;
        this.option2Price = option2Price;
    }

    public int getIndex() {
        return index;
    }

    public double getOption1Price() {
        return option1Price;
    }

    public double getOption2Price() {
        return option2Price;
    }
}
