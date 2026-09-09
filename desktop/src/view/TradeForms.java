package view;

import engine.api.GMEngine;
import engine.api.dto.BuyResultDTO;
import engine.api.dto.CloseResultDTO;
import engine.api.dto.EventDTO;
import engine.api.dto.FillDTO;
import engine.api.dto.OrderQuoteDTO;
import engine.api.dto.OrderResultDTO;
import engine.api.dto.PurchaseQuoteDTO;
import engine.model.OrderSide;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import skin.SkinManager;
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

        Label costLine = new Label();
        Label feeLine = new Label();
        Label totalLine = new Label();
        totalLine.getStyleClass().add("section-title");
        Label afterLine = new Label();
        Label warningLine = warningLabel();

        GridPane grid = grid();
        grid.addRow(0, new Label("Option:"), option);
        grid.addRow(1, new Label("Quantity:"), quantity);
        grid.addRow(2, new Label("Shares cost:"), costLine);
        grid.addRow(3, new Label("Commission:"), feeLine);
        grid.addRow(4, new Label("Total to pay:"), totalLine);
        grid.addRow(5, new Label("Price afterwards:"), afterLine);
        grid.add(warningLine, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(grid);

        // The price comes out of a formula, so it is recalculated live as the
        // quantity or the option changes - never a surprise after confirming.
        Runnable updateQuote = () -> {
            Long amount = tryParseLong(quantity.getText());
            if (amount == null || amount < 1) {
                costLine.setText("-");
                feeLine.setText("-");
                totalLine.setText("-");
                afterLine.setText("-");
                warningLine.setText(quantity.getText().trim().isEmpty()
                        ? "" : "Enter a whole number of 1 or more.");
                return;
            }
            try {
                PurchaseQuoteDTO quote = engine.quoteLmsrPurchase(
                        event.getId(), userName, option.getSelectionModel().getSelectedIndex(), amount);
                costLine.setText(Format.money(quote.getSharesCost())
                        + "   (" + Format.money(quote.getAveragePricePerShare()) + " per share)");
                feeLine.setText(Format.money(quote.getCommission()));
                totalLine.setText(Format.money(quote.getTotalCost()));
                afterLine.setText(Format.money(quote.getPriceAfterwards()));
                if (!quote.isWorthCharging()) {
                    // Below a cent the price stops being a real one, so the
                    // total is shown exactly rather than rounded to $0.00.
                    totalLine.setText(String.format("less than $0.01 (%.8f)", quote.getTotalCost()));
                    warningLine.setText("This option has been pushed so low that the purchase"
                            + " would cost less than $0.01. Buy more of it to reach a real price.");
                } else {
                    warningLine.setText(quote.isAffordable() ? ""
                            : "Not enough money: the balance is "
                                    + Format.money(quote.getBuyerBalance()) + ".");
                }
            } catch (RuntimeException e) {
                costLine.setText("-");
                feeLine.setText("-");
                totalLine.setText("-");
                afterLine.setText("-");
                warningLine.setText(String.valueOf(e.getMessage()));
            }
        };
        quantity.textProperty().addListener((obs, old, value) -> updateQuote.run());
        option.valueProperty().addListener((obs, old, value) -> updateQuote.run());
        updateQuote.run();

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

        Label valueLine = new Label();
        Label feeLine = new Label();
        Label totalLine = new Label();
        totalLine.getStyleClass().add("section-title");
        Label haveLine = new Label();
        Label noteLine = new Label();
        Label warningLine = warningLabel();
        warningLine.setWrapText(true);

        Label totalCaption = new Label("Total to pay:");
        Label haveCaption = new Label("Your balance:");

        GridPane grid = grid();
        grid.addRow(0, new Label("Option:"), option);
        grid.addRow(1, new Label("Side:"), side);
        grid.addRow(2, new Label("Quantity:"), quantity);
        Label priceRange = new Label();
        priceRange.getStyleClass().add("hint-label");
        grid.addRow(3, new Label("Price per share:"), price);
        grid.add(priceRange, 1, 4);
        grid.addRow(5, new Label("Order value:"), valueLine);
        grid.addRow(6, new Label("Commission:"), feeLine);
        grid.addRow(7, totalCaption, totalLine);
        grid.addRow(8, haveCaption, haveLine);
        grid.add(noteLine, 0, 9, 2, 1);
        grid.add(warningLine, 0, 10, 2, 1);
        dialog.getDialogPane().setContent(grid);

        // Recalculated on every keystroke: buying is checked against the
        // balance, selling against the shares actually held.
        Runnable updateQuote = () -> {
            Long amount = tryParseLong(quantity.getText());
            Double limit = tryParseDouble(price.getText());
            if (amount == null || amount < 1 || limit == null) {
                valueLine.setText("-");
                feeLine.setText("-");
                totalLine.setText("-");
                haveLine.setText("-");
                noteLine.setText("");
                warningLine.setText("");
                return;
            }
            try {
                OrderQuoteDTO quote = engine.quoteOrder(event.getId(), userName,
                        option.getSelectionModel().getSelectedIndex(), side.getValue(), limit, amount);

                priceRange.setText("Allowed: " + Format.money(0.01)
                        + " to " + Format.money(quote.getMaxPrice()) + ", in whole cents.");
                valueLine.setText(Format.money(quote.getOrderValue()));
                feeLine.setText(quote.isBuying() ? Format.money(quote.getCommission()) : "none when selling");
                totalCaption.setText(quote.isBuying() ? "Total to pay:" : "You would receive:");
                totalLine.setText(Format.money(quote.getTotalCost()));

                if (quote.isBuying()) {
                    haveCaption.setText("Your balance:");
                    haveLine.setText(Format.money(quote.getBalance()));
                } else {
                    haveCaption.setText("Shares you hold:");
                    haveLine.setText(String.valueOf(quote.getSharesHeld()));
                }

                // An order book needs somebody on the other side. Say so up
                // front, so a resting order is never mistaken for a bet.
                noteLine.setText(marketNote(quote));

                StringBuilder problem = new StringBuilder();
                if (!quote.isPriceValid()) {
                    problem.append("Price must be in whole cents between 0.01 and ")
                            .append(Format.money(quote.getMaxPrice())).append(". ");
                }
                if (quote.isBuying() && !quote.isAffordable()) {
                    problem.append("Not enough money: this needs ")
                            .append(Format.money(quote.getTotalCost()))
                            .append(" but the balance is ")
                            .append(Format.money(quote.getBalance())).append(". ");
                }
                if (!quote.isBuying() && !quote.isEnoughShares()) {
                    problem.append("Not enough shares: only ")
                            .append(quote.getSharesHeld()).append(" held.");
                }
                warningLine.setText(problem.toString().trim());
            } catch (RuntimeException e) {
                valueLine.setText("-");
                feeLine.setText("-");
                totalLine.setText("-");
                haveLine.setText("-");
                noteLine.setText("");
                warningLine.setText(String.valueOf(e.getMessage()));
            }
        };
        quantity.textProperty().addListener((obs, old, value) -> updateQuote.run());
        price.textProperty().addListener((obs, old, value) -> updateQuote.run());
        side.valueProperty().addListener((obs, old, value) -> updateQuote.run());
        option.valueProperty().addListener((obs, old, value) -> updateQuote.run());
        updateQuote.run();

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
            CloseResultDTO result = engine.closeEvent(event.getId(), userName, index);
            Dialogs.info("Event closed", controller.UsersTabController.describeClose(result));
        });
    }

    // ---------------- helpers ----------------

    private static Dialog<ButtonType> baseDialog(String title, String eventName) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ButtonBar.setButtonData(dialog.getDialogPane().lookupButton(ButtonType.OK), ButtonBar.ButtonData.OK_DONE);
        util.DialogChrome.apply(dialog, eventName, null);
        return dialog;
    }

    /**
     * A warning is drawn as a coloured chip, so an empty one would still paint
     * its background and read as a stray mark. It only takes up room while it
     * has something to say.
     */
    /**
     * An order book needs somebody on the other side, so the form says what is
     * actually on offer rather than only whether the order would trade. The
     * figures quoted are the keenest available: the cheapest shares to buy, or
     * the highest bid to sell into.
     */
    static String marketNote(OrderQuoteDTO quote) {
        Double best = quote.getBestOpposingPrice();
        long available = quote.getAvailableNow();

        if (best == null || available <= 0) {
            if (!quote.isBuying()) {
                return "Nobody is bidding for this option right now, so your order will wait in the"
                        + " book until somebody does. No money moves until it trades.";
            }
            // Where the event cannot create shares, no bid on the other option
            // will ever help, and saying otherwise sends the trader looking for
            // something that cannot happen.
            return quote.isMintingAllowed()
                    ? "Nobody is selling this option right now, and nobody is bidding enough on the"
                            + " other one to create new shares. Your order will wait in the book"
                            + " until somebody does. No money moves until it trades."
                    : "Nobody is selling this option right now. This event does not create new"
                            + " shares, so the only way in is from somebody who already holds them"
                            + " and offers them for sale. Your order will wait in the book until"
                            + " one does. No money moves until it trades.";
        }

        String offer = quote.isBuying()
                ? String.format("You can buy %d %s now at %s each.",
                        available, available == 1 ? "share" : "shares", Format.money(best))
                : String.format("You can sell %d %s now at %s each.",
                        available, available == 1 ? "share" : "shares", Format.money(best));

        if (quote.isWouldTradeNow()) {
            return offer + " Your order trades immediately at that price"
                    + (quote.isBuying()
                            ? ", so the amounts above are the most you could pay."
                            : ", so the amounts above are the least you could receive.");
        }
        return offer + (quote.isBuying()
                ? " Your price is below that, so your order waits in the book until somebody"
                        + " accepts it. Raise it to " + Format.money(best) + " to trade at once."
                : " Your price is above that, so your order waits in the book until somebody"
                        + " accepts it. Lower it to " + Format.money(best) + " to trade at once.");
    }

    private static Label warningLabel() {
        Label label = new Label();
        label.getStyleClass().add("blocked-label");
        // A warning that is cut off in the middle is worse than none at all.
        // Wrapping alone is not enough inside a grid: the row has to be told
        // it may grow to whatever the wrapped text needs.
        label.setWrapText(true);
        label.setPrefWidth(300);
        label.setMaxWidth(300);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
        label.managedProperty().bind(label.visibleProperty());
        return label;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        // A long value in the second column must not squeeze the captions in
        // the first one down to "Price afterwa...".
        javafx.scene.layout.ColumnConstraints captions = new javafx.scene.layout.ColumnConstraints();
        captions.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        grid.getColumnConstraints().add(captions);
        return grid;
    }

    /** Quiet parsing for the live preview - a half typed number is not an error. */
    private static Long tryParseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double tryParseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
