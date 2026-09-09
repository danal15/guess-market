package controller;

import anim.AnimationManager;
import engine.api.GMEngine;
import engine.api.dto.EventDTO;
import engine.api.dto.EventFilterDTO;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import util.Format;
import util.Tables;
import view.EventDetailView;

public class EventsTabController {

    private static final String NOTHING_LOADED = "Load a market file to see the events.";
    private static final String NO_MATCHES = "No events match the current filters.";
    private static final String FILE_EMPTY = "This file contains no events.";

    @FXML private HBox methodFilterBox;
    @FXML private HBox statusFilterBox;
    @FXML private HBox commissionFilterBox;
    @FXML private TableView<EventDTO> eventsTable;
    @FXML private ScrollPane detailsHolder;
    @FXML private Label detailsHint;

    private GMEngine engine;
    private final ToggleGroup methodGroup = new ToggleGroup();
    private final ToggleGroup statusGroup = new ToggleGroup();
    private final ToggleGroup commissionGroup = new ToggleGroup();

    @FXML
    private void initialize() {
        buildFilters();
        buildTable();
        detailsHint.setVisible(false);
        detailsHint.setManaged(false);
        showDetails(null);
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
        // One option in each row stays chosen: clicking the selected toggle must not clear it.
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
        eventsTable.getColumns().addAll(java.util.List.of(
                Tables.text("Event", EventDTO::getName),
                Tables.text("Status", EventDTO::getStatusLabel),
                Tables.text("Method", EventDTO::getMethodLabel),
                Tables.text("Commission", event ->
                        Format.percent(event.getCommissionPercent()) + " " + event.getCommissionTypeLabel()),
                Tables.money("Event account", EventDTO::getAccountBalance),
                Tables.text("Market maker", EventDTO::getMarketMakerName)));
        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventsTable.setPlaceholder(new Label(NOTHING_LOADED));
        eventsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> showDetails(selected));
    }

    public void refresh() {
        if (engine == null || !engine.isLoaded()) {
            eventsTable.setPlaceholder(new Label(NOTHING_LOADED));
            eventsTable.setItems(FXCollections.observableArrayList());
            showDetails(null);
            return;
        }

        EventDTO previous = eventsTable.getSelectionModel().getSelectedItem();
        eventsTable.setItems(FXCollections.observableArrayList(engine.getEvents(currentFilter())));
        // Say why the table is empty rather than telling the user to load a file
        // they have already loaded.
        eventsTable.setPlaceholder(new Label(
                engine.getEvents(EventFilterDTO.all()).isEmpty() ? FILE_EMPTY : NO_MATCHES));

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
        boolean hasEvent = event != null;
        detailsHint.setVisible(hasEvent);
        detailsHint.setManaged(hasEvent);

        if (!hasEvent) {
            detailsHolder.setContent(new Label(engine != null && engine.isLoaded()
                    ? "Select an event to see its details."
                    : "Load a market file to see the events."));
            return;
        }
        Node content = event.isOrderBook()
                ? EventDetailView.buildOrderBook(engine.getOrderBookEventState(event.getId()))
                : EventDetailView.buildLmsr(engine.getLmsrEventState(event.getId()));
        detailsHolder.setContent(content);
        AnimationManager.fadeIn(content);
    }
}
