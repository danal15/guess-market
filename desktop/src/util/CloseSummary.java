package util;

import engine.api.dto.CloseResultDTO;

/**
 * Turns the result of closing an event into something worth reading. Both
 * applications close events, so the wording lives here rather than in either
 * one's controller.
 */
public final class CloseSummary {

    private CloseSummary() {
    }

    public static String describe(CloseResultDTO result) {
        StringBuilder message = new StringBuilder();
        message.append("'").append(result.getWinningOptionName()).append("' won.\n\n");
        message.append("Winners paid: ").append(result.getWinnersPaid()).append('\n');
        message.append("Total paid out: ").append(Format.money(result.getTotalPaidOut())).append('\n');
        if (result.getCommissionCollected() > 0) {
            message.append("Commission collected: ")
                    .append(Format.money(result.getCommissionCollected())).append('\n');
        }
        if (result.getReturnedToMarketMaker() > 0) {
            message.append("Returned to the market maker: ")
                    .append(Format.money(result.getReturnedToMarketMaker())).append('\n');
        }
        if (result.getCancelledOrders() > 0) {
            message.append(result.getCancelledOrders())
                    .append(result.getCancelledOrders() == 1
                            ? " resting order was cancelled." : " resting orders were cancelled.");
        }
        return message.toString().trim();
    }
}
