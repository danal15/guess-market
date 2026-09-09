package controller;

import engine.api.GMEngine;
import engine.api.dto.EventDTO;
import engine.api.dto.EventFilterDTO;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import util.Format;
import view.EventDetailView;

public class EventsTabController {

    @FXML private HBox methodFilterBox;
    @FXML private HBox statusFilterBox;
    @FXML private HBox commissionFilterBox;
    @FXML private TableView<EventDTO> eventsTable;
    @FXML private ScrollPane detailsHolder;

    private GMEngine engine;
    private final ToggleGroup methodGroup = new ToggleGroup();
    private final ToggleGroup statusGroup = new ToggleGroup();
    private final ToggleGroup commissionGroup = new ToggleGroup();

    @FXML
    private void initialize() {
        buildFilters();
        buildTable();
        detailsHolder.setContent(new Label("Select an event to see its details."));
    }

    public void setEngine(GMEngine engine) {
        this.engine = engine;
    }

    private void buildFilters() {
        addToggle(methodFilterBox, methodGroup, "All", null, true);
        addToggle(methodFilterBox, methodGroup, "LMSR", Boolean.FALSE, false);
        addToggle(methodFilterBox, methodGroup, "Order Book", Boolean.TRUE, false);

        addToggle(statusFilterBox, statusGroup, "All", null, true);
        addToggle(statusFilterBox, statusGroup, "Not started", "Not started", false);
        addToggle(statusFilterBox, statusGroup, "Active", "Active", false);
        addToggle(statusFilterBox, statusGroup, "Closed", "Closed", false);

        addToggle(commissionFilterBox, commissionGroup, "All", null, true);
        addToggle(commissionFilterBox, commissionGroup, "on-purchase", "on-purchase", false);
        addToggle(commissionFilterBox, commissionGroup, "on-close", "on-close", false);
    }

    private void addToggle(HBox box, ToggleGroup group, String text, Object value, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setUserData(value);
        button.setSelected(selected);
        // Keep one option always chosen: clicking the selected toggle must not clear it.
        button.setOnAction(e -> {
            if (!button.isSelected()) {
                button.setSelected(true);
            } else {
                refresh();
            }
        });
        box.getChildren().add(button);
    }

    private void buildTable() {
        TableColumn<EventDTO, String> name = new TableColumn<>("Event");
        name.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        TableColumn<EventDTO, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getStatusLabel()));
        TableColumn<EventDTO, String> method = new TableColumn<>("Type");
        method.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getMethodLabel()));
        TableColumn<EventDTO, String> commission = new TableColumn<>("Commission");
        commission.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                Format.percent(c.getValue().getCommissionPercent()) + " " + c.getValue().getCommissionTypeLabel()));
        TableColumn<EventDTO, String> account = new TableColumn<>("Account");
        account.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                Format.money(c.getValue().getAccountBalance())));
        TableColumn<EventDTO, String> mm = new TableColumn<>("Market maker");
        mm.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getMarketMakerName()));

        eventsTable.getColumns().addAll(java.util.List.of(name, status, method, commission, account, mm));
        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventsTable.setPlaceholder(new Label("No events. Load a market file to begin."));
        eventsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> showDetails(selected));
    }

    public void refresh() {
        if (engine == null || !engine.isLoaded()) {
            eventsTable.setItems(FXCollections.observableArrayList());
            detailsHolder.setContent(new Label("Load a market file to see events."));
            return;
        }
        EventDTO previous = eventsTable.getSelectionModel().getSelectedItem();
        eventsTable.setItems(FXCollections.observableArrayList(engine.getEvents(currentFilter())));

        if (previous != null) {
            for (EventDTO candidate : eventsTable.getItems()) {
                if (candidate.getId() == previous.getId()) {
                    eventsTable.getSelectionModel().select(candidate);
                    return;
                }
            }
        }
        showDetails(null);
    }

    private EventFilterDTO currentFilter() {
        return new EventFilterDTO(
                (Boolean) valueOf(methodGroup),
                (String) valueOf(statusGroup),
                (String) valueOf(commissionGroup));
    }

    private Object valueOf(ToggleGroup group) {
        return group.getSelectedToggle() == null ? null : group.getSelectedToggle().getUserData();
    }

    private void showDetails(EventDTO event) {
        if (event == null) {
            detailsHolder.setContent(new Label("Select an event to see its details."));
            return;
        }
        if (event.isOrderBook()) {
            detailsHolder.setContent(EventDetailView.buildOrderBook(
                    engine.getOrderBookEventState(event.getId())));
        } else {
            detailsHolder.setContent(EventDetailView.buildLmsr(
                    engine.getLmsrEventState(event.getId())));
        }
    }
}
