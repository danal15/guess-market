package view;

import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.util.StringConverter;

import java.util.List;

/**
 * How one user's account has moved over the course of the market. The engine
 * records the balance after every payment and receipt, so the horizontal axis
 * counts money movements rather than clock time.
 */
public final class BalanceChartView {

    private BalanceChartView() {
    }

    /**
     * @return the chart, or a plain note when the account has not moved yet
     */
    public static Node build(String userName, List<Double> history) {
        if (history == null || history.size() < 2) {
            Label note = new Label("This account has not moved yet, so there is nothing to plot.");
            note.getStyleClass().add("hint-label");
            note.setWrapText(true);
            return note;
        }

        int last = history.size() - 1;
        NumberAxis x = new NumberAxis(0, last, tickFor(last));
        x.setLabel("Money movements");
        x.setMinorTickCount(0);
        x.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number value) {
                return String.valueOf(value.intValue());
            }

            @Override
            public Number fromString(String text) {
                return Integer.valueOf(text);
            }
        });

        // Balances are free to sit anywhere, including below zero once a user
        // has been blocked, so this axis is left to fit the numbers it is given.
        NumberAxis y = new NumberAxis();
        y.setLabel("Balance");
        y.setForceZeroInRange(false);

        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setPrefHeight(200);
        chart.setCreateSymbols(history.size() <= 25);
        chart.setAnimated(false);
        chart.setLegendVisible(false);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(userName);
        for (int i = 0; i < history.size(); i++) {
            series.getData().add(new XYChart.Data<>(i, history.get(i)));
        }
        chart.getData().add(series);
        return chart;
    }

    private static int tickFor(int last) {
        return Math.max(1, (int) Math.ceil(last / 10.0));
    }
}
