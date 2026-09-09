package view;

import engine.api.GMEngine;
import engine.api.dto.BuyResultDTO;
import engine.api.dto.EventDTO;
import engine.api.dto.FillDTO;
import engine.api.dto.OrderResultDTO;
import engine.model.OrderSide;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import util.Dialogs;
import util.Format;

import java.util.Optional;
import java.util.function.Consumer;

/** The small dialogs that collect a trade and hand it to the engine. */
public final class TradeForms {

    private TradeForms() {
    }

    // ---------------- LMSR buy ----------------

    public static void buyLmsr(GMEngine engine, EventDTO event, String userName, Consumer<Runnable> runner) {
        Dialog<ButtonType> dialog = baseDialog("Buy shares", event.getName());

        ChoiceBox<String> option = new ChoiceBox<>();
        option.getItems().addAll(event.getOption1Name(), event.getOption2Name());
        option.getSelectionModel().selectFirst();
        TextField quantity = new TextField("10");

        GridPane grid = grid();
        grid.addRow(0, new Label("Option:"), option);
        grid.addRow(1, new Label("Quantity:"), quantity);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        Long amount = parsePositiveLong(quantity.getText(), "quantity");
        if (amount == null) {
            return;
        }
        int index = option.getSelectionModel().getSelectedIndex();

        runner.accept(() -> {
            BuyResultDTO result = engine.buyLmsrShares(event.getId(), userName, index, amount);
            StringBuilder message = new StringBuilder();
            message.append("Shares: ").append(Format.money(result.getSharesCost())).append('\n');
            message.append("Commission: ").append(Format.money(result.getCommissionPaid())).append('\n');
            message.append("Total paid: ").append(Format.money(result.getTotalPaid())).append('\n');
            message.append("New balance: ").append(Format.money(result.getNewBalance()));
            if (result.isBlockedNow()) {
                message.append("\n\nThis purchase took the account below zero. ")
                        .append(userName).append(" is now blocked from further actions.");
            }
            Dialogs.info("Purchase complete", message.toString());
        });
    }

    // ---------------- order book ----------------

    public static void placeOrder(GMEngine engine, EventDTO event, String userName, Consumer<Runnable> runner) {
        Dialog<ButtonType> dialog = baseDialog("Place an order", event.getName());

        ChoiceBox<String> option = new ChoiceBox<>();
        option.getItems().addAll(event.getOption1Name(), event.getOption2Name());
        option.getSelectionModel().selectFirst();

        ChoiceBox<OrderSide> side = new ChoiceBox<>();
        side.getItems().addAll(OrderSide.BUY, OrderSide.SELL);
        side.getSelectionModel().selectFirst();

        TextField quantity = new TextField("10");
        TextField price = new TextField("0.50");

        GridPane grid = grid();
        grid.addRow(0, new Label("Option:"), option);
        grid.addRow(1, new Label("Side:"), side);
        grid.addRow(2, new Label("Quantity:"), quantity);
        grid.addRow(3, new Label("Price per share:"), price);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        Long amount = parsePositiveLong(quantity.getText(), "quantity");
        if (amount == null) {
            return;
        }
        Double limit = parsePrice(price.getText());
        if (limit == null) {
            return;
        }
        int index = option.getSelectionModel().getSelectedIndex();
        OrderSide chosenSide = side.getValue();

        runner.accept(() -> {
            OrderResultDTO result = engine.placeOrder(event.getId(), userName, index,
                    chosenSide, limit, amount);
            Dialogs.info("Order processed", describe(result, userName));
        });
    }

    private static String describe(OrderResultDTO result, String userName) {
        StringBuilder message = new StringBuilder();
        if (result.getFills().isEmpty()) {
            message.append("Nothing matched right now.\n");
        } else {
            message.append("Matched:\n");
            for (FillDTO fill : result.getFills()) {
                message.append("  ").append(fill.getQuantity())
                        .append(" @ ").append(Format.money(fill.getPrice()))
                        .append("  (").append(fill.getKindLabel());
                if (fill.getCounterpartyName() != null) {
                    message.append(" with ").append(fill.getCounterpartyName());
                }
                message.append(")\n");
            }
        }
        if (result.getMintedQuantity() > 0) {
            message.append("Minted ").append(result.getMintedQuantity()).append(" new share pairs.\n");
        }
        if (result.getRestingQuantity() > 0) {
            message.append(result.getRestingQuantity()).append(" left resting in the book.\n");
        }
        message.append("\nPaid: ").append(Format.money(result.getCashPaid()));
        message.append("\nReceived: ").append(Format.money(result.getCashReceived()));
        message.append("\nCommission: ").append(Format.money(result.getCommissionPaid()));
        message.append("\nNew balance: ").append(Format.money(result.getNewBalance()));
        if (result.isBlockedNow()) {
            message.append("\n\nThis order took the account below zero. ")
                    .append(userName).append(" is now blocked from further actions.");
        }
        return message.toString();
    }

    // ---------------- closing ----------------

    public static void closeEvent(GMEngine engine, EventDTO event, String userName, Consumer<Runnable> runner) {
        Dialog<ButtonType> dialog = baseDialog("Close event", event.getName());

        ChoiceBox<String> option = new ChoiceBox<>();
        option.getItems().addAll(event.getOption1Name(), event.getOption2Name());
        option.getSelectionModel().selectFirst();

        GridPane grid = grid();
        grid.addRow(0, new Label("Winning option:"), option);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        int index = option.getSelectionModel().getSelectedIndex();
        String winner = option.getValue();
        if (!Dialogs.confirm("Close '" + event.getName() + "'?",
                "'" + winner + "' will be declared the winner. This cannot be undone.")) {
            return;
        }
        runner.accept(() -> {
            engine.closeEvent(event.getId(), userName, index);
            Dialogs.info("Event closed", "'" + winner + "' won. Winners have been paid.");
        });
    }

    // ---------------- helpers ----------------

    private static Dialog<ButtonType> baseDialog(String title, String eventName) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(eventName);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ButtonBar.setButtonData(dialog.getDialogPane().lookupButton(ButtonType.OK), ButtonBar.ButtonData.OK_DONE);
        return dialog;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        return grid;
    }

    private static Long parsePositiveLong(String text, String what) {
        try {
            long value = Long.parseLong(text.trim());
            if (value < 1) {
                Dialogs.error("Invalid " + what, "The " + what + " must be at least 1.");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            Dialogs.error("Invalid " + what, "'" + text + "' is not a whole number.");
            return null;
        }
    }

    private static Double parsePrice(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            Dialogs.error("Invalid price", "'" + text + "' is not a valid price. Use a value such as 0.42.");
            return null;
        }
    }
}
