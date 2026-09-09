package util;

/** Every number shown to the user goes through here, so rounding and units are done in one place. */
public final class Format {

    private Format() {
    }

    /** Money always carries a marker so it can never be mistaken for a share count. */
    /** Amounts too small to show as a cent are plain zero, never "-$0.00". */
    public static String money(double value) {
        return value <= -0.005
                ? String.format("-$%.2f", Math.abs(value))
                : String.format("$%.2f", Math.max(value, 0.0));
    }

    /** Prices that are not defined yet are shown as a dash rather than a misleading zero. */
    public static String price(Double value) {
        return value == null ? "-" : money(value);
    }

    /** A plain two decimal number, for values that are not money. */
    public static String decimal(double value) {
        return String.format("%.2f", value);
    }

    public static String percent(int value) {
        return value + "%";
    }

    public static String signed(double value) {
        // Anything under half a cent reads as no change at all, so it does not
        // get a sign that suggests the balance moved.
        if (Math.abs(value) < 0.005) {
            return "$0.00";
        }
        return (value >= 0 ? "+" : "-") + String.format("$%.2f", Math.abs(value));
    }

    public static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }
}
