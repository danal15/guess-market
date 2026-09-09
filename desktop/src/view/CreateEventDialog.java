package view;

import engine.api.dto.NewEventRequestDTO;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import skin.SkinManager;
import util.Dialogs;

import java.util.Optional;

/** Bonus: lets a user invent a brand new event and become its market maker. */
public final class CreateEventDialog {

    private CreateEventDialog() {
    }

    public static NewEventRequestDTO show(String ownerName) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create a new event");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        util.DialogChrome.apply(dialog,
                ownerName + " will be the market maker of this event.", null);

        TextField name = new TextField();
        TextArea description = new TextArea();
        description.setPrefRowCount(2);
        TextField commission = new TextField("5");
        ChoiceBox<String> commissionType = new ChoiceBox<>();
        commissionType.getItems().addAll("on-purchase", "on-close");
        commissionType.getSelectionModel().selectFirst();
        TextField option1 = new TextField("Yes");
        TextField option2 = new TextField("No");

        ChoiceBox<String> method = new ChoiceBox<>();
        method.getItems().addAll("LMSR", "Order Book");
        method.getSelectionModel().selectFirst();

        TextField b = new TextField("100");
        TextField baseValue = new TextField("1");
        TextField initial = new TextField("100");
        CheckBox allowMint = new CheckBox("Allow minting");
        allowMint.setSelected(true);

        Label bLabel = new Label("Liquidity b:");
        Label dLabel = new Label("Base value d:");
        Label initialLabel = new Label("Initial investment:");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        int row = 0;
        grid.addRow(row++, new Label("Name:"), name);
        grid.addRow(row++, new Label("Description:"), description);
        grid.addRow(row++, new Label("Commission %:"), commission);
        grid.addRow(row++, new Label("Collected:"), commissionType);
        grid.addRow(row++, new Label("Option 1:"), option1);
        grid.addRow(row++, new Label("Option 2:"), option2);
        grid.addRow(row++, new Label("Method:"), method);
        grid.addRow(row++, bLabel, b);
        grid.addRow(row++, dLabel, baseValue);
        grid.addRow(row++, initialLabel, initial);
        grid.add(allowMint, 1, row);
        dialog.getDialogPane().setContent(grid);

        // Hidden rows must also be unmanaged, otherwise they leave empty gaps
        // and the dialog never resizes to fit the method that is chosen.
        Runnable syncMethodFields = () -> {
            boolean orderBook = "Order Book".equals(method.getValue());
            show(bLabel, !orderBook);
            show(b, !orderBook);
            show(dLabel, orderBook);
            show(baseValue, orderBook);
            show(initialLabel, orderBook);
            show(initial, orderBook);
            show(allowMint, orderBook);
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        };
        method.setOnAction(e -> syncMethodFields.run());
        syncMethodFields.run();

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return null;
        }

        boolean orderBook = "Order Book".equals(method.getValue());
        Integer commissionPercent = parseInt(commission.getText(), "commission");
        if (commissionPercent == null) {
            return null;
        }
        Integer bValue = 0;
        Integer dValue = 0;
        Integer initialValue = 0;
        if (orderBook) {
            dValue = parseInt(baseValue.getText(), "base value");
            initialValue = parseInt(initial.getText(), "initial investment");
            if (dValue == null || initialValue == null) {
                return null;
            }
        } else {
            bValue = parseInt(b.getText(), "liquidity b");
            if (bValue == null) {
                return null;
            }
        }

        return new NewEventRequestDTO(name.getText(), description.getText(), commissionPercent,
                commissionType.getValue(), option1.getText(), option2.getText(),
                orderBook, bValue, dValue, initialValue, allowMint.isSelected());
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static Integer parseInt(String text, String what) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            Dialogs.error("Invalid " + what, "'" + text + "' is not a whole number.");
            return null;
        }
    }
}
