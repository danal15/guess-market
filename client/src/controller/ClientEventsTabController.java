package controller;

import anim.AnimationManager;
import com.google.gson.Gson;
import engine.api.dto.EventDTO;
import engine.api.dto.EventFilterDTO;
import engine.api.dto.LmsrEventStateDTO;
import engine.api.dto.OrderBookEventStateDTO;
import engine.api.dto.SnapshotDTO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import net.MarketClient;
import util.Format;
import util.Tables;
import view.EventDetailView;

import java.util.ArrayList;
import java.util.List;

/**
 * Every event in the market, for looking at. Trading happens in the Account tab,
 * because an action belongs to the user performing it.
 *
 * The filters are applied here rather than by the server: one poll brings back
 * the whole market, and narrowing it down locally means moving a toggle shows
 * its effect at once instead of waiting for the next request.
 */
public class ClientEventsTabController {

    private static final String NOTHING_YET =
            "No events yet. Upload a file of events from the Account tab to start one.";
    private static final String NO_MATCHES = "No events match the current filters.";

    @FXML private HBox methodFilterBox;
    @FXML private HBox statusFilterBox;
    @FXML private HBox commissionFilterBox;
    @FXML private TableView<EventDTO> eventsTable;
    @FXML private ScrollPane detailsHolder;
    @FXML private Label detailsHint;

    private final ToggleGroup methodGroup = new ToggleGroup();
    private final ToggleGroup statusGroup = new ToggleGroup();
    private final ToggleGroup commissionGroup = new ToggleGroup();

    /** The most recent picture of the market, so a filter change needs no request. */
    private SnapshotDTO latest;

    /**
     * The table keeps one list for its whole life and the rows are replaced
     * inside it. Handing it a brand new list every second would throw away the
     * column the user had sorted by, once a second, for ever.
     */
    private final ObservableList<EventDTO> rows = FXCollections.observableArrayList();

    /**
     * What the details pane was last built from. The pane is a tree of tables
     * and a chart, and rebuilding it every second would lose any sorting or
     * scrolling inside it and, with animations on, keep it fading for ever. So
     * it is only rebuilt when the thing it is showing has actually changed.
     */
    private String shownDetails;
    private static final Gson FINGERPRINT = new Gson();

    @FXML
    private void initialize() {
        buildFilters();
        buildTable();
        detailsHint.setVisible(false);
        detailsHint.setManaged(false);
        detailsHolder.setContent(new Label(NOTHING_YET));
    }

    public void start(MarketClient client) {
        // Everything this screen shows arrives with the poll, so there is nothing
        // to hold on to here.
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
        button.setOnAction(event -> {
            if (!button.isSelected()) {
                button.setSelected(true);
            } else {
                showEvents();
            }
        });
        box.getChildren().add(button);
    }

    private void buildTable() {
        eventsTable.getColumns().addAll(List.of(
                Tables.text("Event", EventDTO::getName),
                Tables.text("Status", EventDTO::getStatusLabel),
                Tables.text("Method", EventDTO::getMethodLabel),
                Tables.text("Commission", event ->
                        Format.percent(event.getCommissionPercent()) + " " + event.getCommissionTypeLabel()),
                Tables.money("Event account", EventDTO::getAccountBalance),
                Tables.text("Market maker", EventDTO::getMarketMakerName)));
        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventsTable.setPlaceholder(new Label(NOTHING_YET));
        eventsTable.setItems(rows);
        eventsTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, old, selected) -> showDetails(selected));
    }

    /** The event whose details are open, so the next poll fetches its state too. */
    public Integer getSelectedEventId() {
        EventDTO selected = eventsTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    public void apply(SnapshotDTO snapshot) {
        this.latest = snapshot;
        showEvents();
        showDetails(eventsTable.getSelectionModel().getSelectedItem());
    }

    /**
     * Rebuilds the list, putting the selection back on the same event. Without
     * that the row under the pointer would jump away once a second.
     */
    private void showEvents() {
        if (latest == null) {
            return;
        }
        EventDTO previous = eventsTable.getSelectionModel().getSelectedItem();

        EventFilterDTO filter = currentFilter();
        List<EventDTO> matching = new ArrayList<>();
        for (EventDTO event : latest.getEvents()) {
            if (filter.matches(event)) {
                matching.add(event);
            }
        }
        rows.setAll(matching);
        eventsTable.setPlaceholder(new Label(
                latest.getTotalEventCount() == 0 ? NOTHING_YET : NO_MATCHES));

        if (previous != null) {
            for (EventDTO candidate : matching) {
                if (candidate.getId() == previous.getId()) {
                    eventsTable.getSelectionModel().select(candidate);
                    return;
                }
            }
        }
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

        if (!hasEvent || latest == null) {
            String nothing = latest == null || latest.getTotalEventCount() == 0
                    ? NOTHING_YET
                    : "Select an event to see its details.";
            if (!nothing.equals(shownDetails)) {
                shownDetails = nothing;
                detailsHolder.setContent(new Label(nothing));
            }
            return;
        }

        // The poll only carries the detail of the event that was selected when it
        // was sent, so just after a click there is nothing yet. Saying so beats
        // blanking the panel for a moment.
        Object state = null;
        if (latest.getOrderBookState() != null
                && latest.getOrderBookState().getEvent().getId() == event.getId()) {
            state = latest.getOrderBookState();
        } else if (latest.getLmsrState() != null
                && latest.getLmsrState().getEvent().getId() == event.getId()) {
            state = latest.getLmsrState();
        }

        String fingerprint = state == null
                ? "loading:" + event.getId()
                : FINGERPRINT.toJson(state);
        if (fingerprint.equals(shownDetails)) {
            return;
        }
        shownDetails = fingerprint;

        Node content = state == null
                ? new Label("Loading '" + event.getName() + "'...")
                : state instanceof OrderBookEventStateDTO book
                        ? EventDetailView.buildOrderBook(book)
                        : EventDetailView.buildLmsr((LmsrEventStateDTO) state);
        detailsHolder.setContent(content);
        AnimationManager.fadeIn(content);
    }
}
