package util;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.util.function.Function;

/**
 * Builds table columns. Numeric columns keep their real type so that sorting
 * compares values rather than the text of those values, and they are right
 * aligned so decimal points line up down the column.
 */
public final class Tables {

    private Tables() {
    }

    public static <S> TableColumn<S, String> text(String title, Function<S, String> extractor) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new SimpleStringProperty(extractor.apply(c.getValue())));
        return column;
    }

    public static <S> TableColumn<S, String> yesNo(String title, Function<S, Boolean> extractor) {
        return text(title, row -> Format.yesNo(extractor.apply(row)));
    }

    /** A money column: sorted numerically, shown with a currency marker, right aligned. */
    public static <S> TableColumn<S, Double> money(String title, Function<S, Double> extractor) {
        TableColumn<S, Double> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new SimpleObjectProperty<>(extractor.apply(c.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : Format.money(value));
                getStyleClass().add("numeric-cell");
            }
        });
        return column;
    }

    /** A whole number column such as a share count: sorted numerically, right aligned. */
    public static <S> TableColumn<S, Long> count(String title, Function<S, Long> extractor) {
        TableColumn<S, Long> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new SimpleObjectProperty<>(extractor.apply(c.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Long value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.valueOf(value));
                getStyleClass().add("numeric-cell");
            }
        });
        return column;
    }
}
