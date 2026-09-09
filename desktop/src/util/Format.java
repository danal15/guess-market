package util;

/** Every number shown to the user goes through here, so rounding is done in one place. */
public final class Format {

    private Format() {
    }

    public static String money(double value) {
        return String.format("%.2f", value);
    }

    /** Prices that are not defined yet are shown as a dash rather than a misleading zero. */
    public static String price(Double value) {
        return value == null ? "-" : String.format("%.2f", value);
    }

    public static String percent(int value) {
        return value + "%";
    }

    public static String signed(double value) {
        return (value >= 0 ? "+" : "") + String.format("%.2f", value);
    }
}
