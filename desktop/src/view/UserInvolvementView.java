package view;

import engine.api.dto.UserEventInvolvementDTO;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import util.Format;

/** How the selected user is involved in the selected event. */
public final class UserInvolvementView {

    private UserInvolvementView() {
    }

    public static Node build(UserEventInvolvementDTO involvement) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(4));

        Label title = new Label("Your involvement in '" + involvement.getEventName() + "'"
                + (involvement.isMarketMaker() ? "  (you are the market maker)" : ""));
        title.getStyleClass().add("section-title");
        box.getChildren().add(title);

        boolean closed = involvement.getWinningOptionName() != null;
        if (involvement.isOrderBook()) {
            box.getChildren().add(optionTotals(involvement, "Shares held"));
        } else {
            box.getChildren().add(new Label("Your trades in this event:"));
            box.getChildren().add(EventDetailView.tradesTable(involvement.getTradesNewestFirst()));
            // Once the event is decided the running total per option says more
            // than the individual rows, which by then can be a long list.
            if (closed) {
                box.getChildren().add(optionTotals(involvement, "Shares bought"));
            }
        }

        box.getChildren().add(new Label("Commission you paid: " + Format.money(involvement.getCommissionPaid())));

        if (involvement.getOpenOrderCount() > 0) {
            Label waiting = new Label(involvement.getOpenOrderCount()
                    + (involvement.getOpenOrderCount() == 1 ? " order" : " orders")
                    + " of yours (" + involvement.getOpenOrderQuantity()
                    + " shares) are still waiting in the book and have not traded yet.");
            waiting.setWrapText(true);
            waiting.getStyleClass().add("blocked-label");
            box.getChildren().add(waiting);
        }

        if (closed) {
            box.getChildren().add(new Label("Winning option: " + involvement.getWinningOptionName()));
            if (involvement.getProfitOrLoss() == null) {
                // Never traded here, so there is no position to report a result on.
                Label none = new Label("You did not take part in this event, so closing it"
                        + " did not change your balance.");
                none.setWrapText(true);
                none.getStyleClass().add("hint-label");
                box.getChildren().add(none);
            } else {
                box.getChildren().add(resultBlock(involvement));
            }
        }
        return box;
    }

    /**
     * Running an event and trading in it are two different pots of money. Shown
     * separately when the user did both, so the final figure adds up to the
     * change the user can see in their balance.
     */
    private static Node resultBlock(UserEventInvolvementDTO involvement) {
        VBox box = new VBox(2);
        box.setPadding(new Insets(6, 0, 0, 0));

        boolean ranTheEvent = involvement.getMarketMakerPaid() > 0
                || involvement.getMarketMakerReceived() > 0;

        if (ranTheEvent && involvement.getTradingResult() != null) {
            GridPane grid = new GridPane();
            grid.setHgap(14);
            grid.setVgap(2);
            int row = 0;
            grid.addRow(row++, new Label("From trading:"),
                    new Label(Format.signed(involvement.getTradingResult())));
            if (involvement.getMarketMakerPaid() > 0) {
                grid.addRow(row++, new Label("Put in to run the event:"),
                        new Label(Format.signed(-involvement.getMarketMakerPaid())));
            }
            if (involvement.getMarketMakerReceived() > 0) {
                grid.addRow(row++, new Label("Taken back as market maker:"),
                        new Label(Format.signed(involvement.getMarketMakerReceived())));
            }
            box.getChildren().add(grid);
        }

        Label total = new Label("Your result from this event: "
                + Format.signed(involvement.getProfitOrLoss()));
        total.getStyleClass().add("section-title");
        box.getChildren().add(total);

        boolean heldNothing = involvement.getOption1Quantity() == 0
                && involvement.getOption2Quantity() == 0;
        Label note = new Label(heldNothing && !ranTheEvent
                ? "You held no shares when the event closed, so nothing was paid out to you."
                        + " Any order of yours that had not traded was cancelled."
                : "This is the total change to your balance from this event.");
        note.setWrapText(true);
        note.getStyleClass().add("hint-label");
        box.getChildren().add(note);
        return box;
    }

    /** What the user ended up holding in each option, and what it cost them. */
    private static GridPane optionTotals(UserEventInvolvementDTO involvement, String quantityHeader) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(3);
        grid.addRow(0, header("Option"), header(quantityHeader), header("Paid for them"));
        grid.addRow(1, new Label(involvement.getOption1Name()),
                new Label(String.valueOf(involvement.getOption1Quantity())),
                new Label(Format.money(involvement.getOption1Paid())));
        grid.addRow(2, new Label(involvement.getOption2Name()),
                new Label(String.valueOf(involvement.getOption2Quantity())),
                new Label(Format.money(involvement.getOption2Paid())));
        return grid;
    }

    private static Label header(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }
}
