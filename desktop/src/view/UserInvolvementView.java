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

        if (involvement.isOrderBook()) {
            GridPane grid = new GridPane();
            grid.setHgap(14);
            grid.setVgap(3);
            grid.addRow(0, header("Option"), header("Shares held"), header("Paid for them"));
            grid.addRow(1, new Label(involvement.getOption1Name()),
                    new Label(String.valueOf(involvement.getOption1Quantity())),
                    new Label(Format.money(involvement.getOption1Paid())));
            grid.addRow(2, new Label(involvement.getOption2Name()),
                    new Label(String.valueOf(involvement.getOption2Quantity())),
                    new Label(Format.money(involvement.getOption2Paid())));
            box.getChildren().add(grid);
        } else {
            box.getChildren().add(new Label("Your trades in this event:"));
            box.getChildren().add(EventDetailView.tradesTable(involvement.getTradesNewestFirst()));
        }

        box.getChildren().add(new Label("Commission you paid: " + Format.money(involvement.getCommissionPaid())));

        if (involvement.getWinningOptionName() != null) {
            box.getChildren().add(new Label("Winning option: " + involvement.getWinningOptionName()));
            if (involvement.getProfitOrLoss() != null) {
                Label result = new Label("Your result from this event: "
                        + Format.signed(involvement.getProfitOrLoss()));
                result.getStyleClass().add("section-title");
                box.getChildren().add(result);
            }
        }
        return box;
    }

    private static Label header(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }
}
