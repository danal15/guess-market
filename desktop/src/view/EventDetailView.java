package view;

import engine.api.dto.LmsrEventStateDTO;
import engine.api.dto.OrderBookEventStateDTO;
import engine.api.dto.OrderBookSideDTO;
import engine.api.dto.OrderBookStatsDTO;
import engine.api.dto.OrderDTO;
import engine.api.dto.ParticipantDTO;
import engine.api.dto.PricePointDTO;
import engine.api.dto.TradeDTO;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import util.Format;
import util.Tables;

import java.util.List;

/**
 * Builds the read only detail views for an event. Shared by the events screen
 * and the users screen so both show exactly the same information.
 */
public final class EventDetailView {

    private EventDetailView() {
    }

    // ---------------- LMSR ----------------

    public static Node buildLmsr(LmsrEventStateDTO state) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(4));

        box.getChildren().add(title(state.getEvent().getName() + "  -  LMSR"));
        box.getChildren().add(wrapped(state.getEvent().getDescription()));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(4);
        grid.addRow(0, title("Option"), title("Price"), title("Shares bought"));
        grid.addRow(1, new Label(state.getOption1State().getName()),
                new Label(Format.money(state.getOption1State().getPrice())),
                new Label(String.valueOf(state.getOption1State().getSharesOutstanding())));
        grid.addRow(2, new Label(state.getOption2State().getName()),
                new Label(Format.money(state.getOption2State().getPrice())),
                new Label(String.valueOf(state.getOption2State().getSharesOutstanding())));
        box.getChildren().add(grid);

        box.getChildren().add(new Label("Event account: " + Format.money(state.getAccountBalance())));
        box.getChildren().add(new Label("Commission collected: "
                + Format.money(state.getTotalCommissionCollected())));
        if (state.getWinningOptionName() != null) {
            box.getChildren().add(title("Closed - winning option: " + state.getWinningOptionName()));
        }

        box.getChildren().add(title("Trade history (newest first)"));
        box.getChildren().add(tradesTable(state.getTradesNewestFirst()));

        if (state.getPriceHistory().size() > 1) {
            box.getChildren().add(title("Price history"));
            box.getChildren().add(priceChart(state.getPriceHistory(),
                    state.getOption1State().getName(), state.getOption2State().getName()));
        }
        return box;
    }

    public static TableView<TradeDTO> tradesTable(List<TradeDTO> trades) {
        TableView<TradeDTO> table = new TableView<>(FXCollections.observableArrayList(trades));
        table.setPlaceholder(new Label("No trades in this event yet."));
        table.setPrefHeight(180);
        table.getColumns().addAll(List.of(
                Tables.text("User", TradeDTO::getUserName),
                Tables.text("Option", TradeDTO::getOptionName),
                Tables.count("Quantity", TradeDTO::getQuantity),
                Tables.money("Price paid", TradeDTO::getSharesCost),
                Tables.money("Commission", TradeDTO::getCommissionPaid)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        return table;
    }

    // ---------------- Order book ----------------

    public static Node buildOrderBook(OrderBookEventStateDTO state) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(4));

        box.getChildren().add(title(state.getEvent().getName() + "  -  Order Book"));
        box.getChildren().add(wrapped(state.getEvent().getDescription()));
        box.getChildren().add(new Label("Base value: " + Format.money(state.getBaseValue())
                + "   |   Minting " + (state.isAllowMint() ? "allowed" : "not allowed")));
        box.getChildren().add(new Label("Event account: " + Format.money(state.getAccountBalance())));
        box.getChildren().add(new Label("Commission collected: "
                + Format.money(state.getTotalCommissionCollected())));
        if (state.getWinningOptionName() != null) {
            box.getChildren().add(title("Closed - winning option: " + state.getWinningOptionName()));
        }

        HBox books = new HBox(10);
        Node first = bookPane(state.getOption1Book());
        Node second = bookPane(state.getOption2Book());
        HBox.setHgrow(first, Priority.ALWAYS);
        HBox.setHgrow(second, Priority.ALWAYS);
        books.getChildren().addAll(first, second);
        box.getChildren().add(books);

        box.getChildren().add(title("Participants"));
        box.getChildren().add(participantsTable(state.getParticipants(),
                state.getOption1Book().getOptionName(), state.getOption2Book().getOptionName()));

        if (state.getPriceHistory().size() > 1) {
            box.getChildren().add(title("Price history"));
            box.getChildren().add(priceChart(state.getPriceHistory(),
                    state.getOption1Book().getOptionName(), state.getOption2Book().getOptionName()));
        }
        return box;
    }

    private static Node bookPane(OrderBookSideDTO side) {
        VBox box = new VBox(6);
        box.getChildren().add(title(side.getOptionName()));
        box.getChildren().add(new Label("Shares outstanding: " + side.getSharesOutstanding()));

        OrderBookStatsDTO stats = side.getStats();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        grid.addRow(0, new Label("Last trade"), new Label(Format.price(stats.getLastTradePrice())));
        grid.addRow(1, new Label("Best bid"), new Label(Format.price(stats.getBestBid())));
        grid.addRow(2, new Label("Best ask"), new Label(Format.price(stats.getBestAsk())));
        grid.addRow(3, new Label("Mid price"), new Label(Format.price(stats.getMidPrice())));
        grid.addRow(4, new Label("Spread"), new Label(Format.price(stats.getSpread())));
        box.getChildren().add(grid);

        box.getChildren().add(new Label("Bids (buy)"));
        box.getChildren().add(ordersTable(side.getBids(), "No bids."));
        box.getChildren().add(new Label("Asks (sell)"));
        box.getChildren().add(ordersTable(side.getAsks(), "No asks."));
        return box;
    }

    private static TableView<OrderDTO> ordersTable(List<OrderDTO> orders, String emptyText) {
        TableView<OrderDTO> table = new TableView<>(FXCollections.observableArrayList(orders));
        table.setPlaceholder(new Label(emptyText));
        table.setPrefHeight(130);
        table.getColumns().addAll(List.of(
                Tables.text("User", OrderDTO::getUserName),
                Tables.count("Quantity", OrderDTO::getQuantity),
                Tables.money("Price", OrderDTO::getPrice)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        return table;
    }

    private static TableView<ParticipantDTO> participantsTable(List<ParticipantDTO> participants,
                                                               String option1, String option2) {
        TableView<ParticipantDTO> table = new TableView<>(FXCollections.observableArrayList(participants));
        table.setPlaceholder(new Label("Nobody has taken part in this event yet."));
        table.setPrefHeight(160);
        table.getColumns().addAll(List.of(
                Tables.text("User", ParticipantDTO::getUserName),
                Tables.count(option1 + " qty", ParticipantDTO::getOption1Quantity),
                Tables.money(option1 + " value", ParticipantDTO::getOption1Value),
                Tables.count(option2 + " qty", ParticipantDTO::getOption2Quantity),
                Tables.money(option2 + " value", ParticipantDTO::getOption2Value),
                Tables.yesNo("Open orders", ParticipantDTO::isHasRestingOrders)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        return table;
    }

    // ---------------- shared bits ----------------

    private static Node priceChart(List<PricePointDTO> history, String option1, String option2) {
        NumberAxis x = new NumberAxis();
        x.setLabel("Trades so far");
        NumberAxis y = new NumberAxis();
        y.setLabel("Price");

        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setPrefHeight(240);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);

        XYChart.Series<Number, Number> first = new XYChart.Series<>();
        first.setName(option1);
        XYChart.Series<Number, Number> second = new XYChart.Series<>();
        second.setName(option2);
        for (PricePointDTO point : history) {
            first.getData().add(new XYChart.Data<>(point.getIndex(), point.getOption1Price()));
            second.getData().add(new XYChart.Data<>(point.getIndex(), point.getOption2Price()));
        }
        chart.getData().add(first);
        chart.getData().add(second);
        return chart;
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }
}
