package controller;

import anim.AnimationManager;
import engine.api.GMEngine;
import engine.api.dto.CloseResultDTO;
import engine.api.dto.EventDTO;
import engine.api.dto.NewEventRequestDTO;
import engine.api.dto.UserDTO;
import engine.api.dto.UserEventInvolvementDTO;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import util.Dialogs;
import util.Format;
import util.Tables;
import view.CreateEventDialog;
import view.BalanceChartView;
import view.EventDetailView;
import view.TradeForms;
import view.UserInvolvementView;

public class UsersTabController {

    @FXML private TableView<UserDTO> usersTable;
    @FXML private TableView<EventDTO> userEventsTable;
    @FXML private ScrollPane userDetailsHolder;
    @FXML private Label balanceLabel;
    @FXML private Label blockedLabel;
    @FXML private HBox actionBar;
    @FXML private Button createEventButton;

    private GMEngine engine;
    private Runnable onChanged = () -> { };
    /** Remembered so acting on an event does not silently collapse the pane again. */
    private boolean fullDetailsExpanded;
    private boolean balanceChartExpanded = true;

    @FXML
    private void initialize() {
        buildUsersTable();
        buildUserEventsTable();
        userDetailsHolder.setContent(new Label("Load a market file to see users."));
        createEventButton.setDisable(true);
    }

    public void setEngine(GMEngine engine) {
        this.engine = engine;
    }

    /** Called after any action so the whole window reloads from the engine. */
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    private void buildUsersTable() {
        usersTable.getColumns().addAll(java.util.List.of(
                Tables.text("User", UserDTO::getName),
                Tables.money("Balance", UserDTO::getBalance),
                Tables.yesNo("Market maker", UserDTO::isMarketMaker),
                Tables.yesNo("Blocked", UserDTO::isBlocked)));
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        usersTable.setPlaceholder(new Label("Load a market file to see users."));
        usersTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> onUserSelected(selected));
    }

    private void buildUserEventsTable() {
        userEventsTable.getColumns().addAll(java.util.List.of(
                Tables.text("Event", EventDTO::getName),
                Tables.text("Status", EventDTO::getStatusLabel),
                Tables.text("Method", EventDTO::getMethodLabel),
                Tables.text("Your role", event -> {
                    UserDTO user = usersTable.getSelectionModel().getSelectedItem();
                    if (user == null) {
                        return "";
                    }
                    if (event.getMarketMakerName().equals(user.getName())) {
                        return "market maker";
                    }
                    return engine.isParticipant(user.getName(), event.getId())
                            ? "taking part" : "not involved yet";
                })));
        userEventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        userEventsTable.setPlaceholder(new Label("Load a market file to see the events."));
        userEventsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> onEventSelected(selected));
    }

    public void refresh() {
        if (engine == null || !engine.isLoaded()) {
            usersTable.setItems(FXCollections.observableArrayList());
            userEventsTable.setItems(FXCollections.observableArrayList());
            userDetailsHolder.setContent(new Label("Load a market file to see users."));
            balanceLabel.setText("Balance: -");
            setBlocked(false);
            actionBar.getChildren().clear();
            createEventButton.setDisable(true);
            return;
        }

        UserDTO previousUser = usersTable.getSelectionModel().getSelectedItem();
        EventDTO previousEvent = userEventsTable.getSelectionModel().getSelectedItem();

        usersTable.setItems(FXCollections.observableArrayList(engine.getUsers()));
        if (previousUser != null) {
            for (UserDTO candidate : usersTable.getItems()) {
                if (candidate.getName().equals(previousUser.getName())) {
                    usersTable.getSelectionModel().select(candidate);
                    break;
                }
            }
        }

        UserDTO user = usersTable.getSelectionModel().getSelectedItem();
        if (user != null && previousEvent != null) {
            for (EventDTO candidate : userEventsTable.getItems()) {
                if (candidate.getId() == previousEvent.getId()) {
                    userEventsTable.getSelectionModel().select(candidate);
                    break;
                }
            }
        }
    }

    private void onUserSelected(UserDTO user) {
        actionBar.getChildren().clear();
        if (user == null) {
            balanceLabel.setText("Balance: -");
            setBlocked(false);
            userEventsTable.setItems(FXCollections.observableArrayList());
            userDetailsHolder.setContent(new Label("Select a user to see what they are taking part in."));
            createEventButton.setDisable(true);
            return;
        }
        balanceLabel.setText("Balance: " + Format.money(user.getBalance()));
        setBlocked(user.isBlocked());
        createEventButton.setDisable(user.isBlocked());
        createEventButton.setTooltip(user.isBlocked()
                ? new Tooltip(user.getName() + " is blocked and cannot create events.")
                : new Tooltip("Create a new event with " + user.getName() + " as its market maker."));

        EventDTO previous = userEventsTable.getSelectionModel().getSelectedItem();
        userEventsTable.setItems(FXCollections.observableArrayList(engine.getUserEvents(user.getName())));
        if (previous != null) {
            for (EventDTO candidate : userEventsTable.getItems()) {
                if (candidate.getId() == previous.getId()) {
                    userEventsTable.getSelectionModel().select(candidate);
                    return;
                }
            }
        }
        onEventSelected(userEventsTable.getSelectionModel().getSelectedItem());
    }

    /** The user's account over time, in a pane that remembers being folded away. */
    private TitledPane balancePane(UserDTO user) {
        TitledPane pane = new TitledPane("Balance history for " + user.getName(),
                BalanceChartView.build(user.getName(),
                        engine.getUserBalanceHistory(user.getName())));
        pane.setExpanded(balanceChartExpanded);
        pane.expandedProperty().addListener((obs, old, expanded) -> balanceChartExpanded = expanded);
        return pane;
    }

    private void setBlocked(boolean blocked) {
        blockedLabel.setText(blocked ? "Blocked" : "");
        blockedLabel.setVisible(blocked);
        blockedLabel.setManaged(blocked);
        blockedLabel.setTooltip(blocked
                ? new Tooltip("This account went below zero and can no longer act.")
                : null);
    }

    private void onEventSelected(EventDTO event) {
        actionBar.getChildren().clear();
        UserDTO user = usersTable.getSelectionModel().getSelectedItem();
        if (user == null || event == null) {
            VBox empty = new VBox(10);
            empty.getChildren().add(new Label("Select one of this user's events."));
            if (user != null) {
                empty.getChildren().add(balancePane(user));
            }
            userDetailsHolder.setContent(empty);
            return;
        }

        VBox content = new VBox(10);
        UserEventInvolvementDTO involvement = engine.getUserInvolvement(user.getName(), event.getId());
        content.getChildren().add(UserInvolvementView.build(involvement));

        // The account chart belongs to the user rather than to the event, so it
        // stays on screen whichever of their events is being looked at.
        content.getChildren().add(balancePane(user));

        // The full event view is available but collapsed, so the part that is
        // about this user stays visible without scrolling.
        TitledPane full = new TitledPane("Full event details", event.isOrderBook()
                ? EventDetailView.buildOrderBook(engine.getOrderBookEventState(event.getId()))
                : EventDetailView.buildLmsr(engine.getLmsrEventState(event.getId())));
        full.setExpanded(fullDetailsExpanded);
        full.expandedProperty().addListener((obs, old, expanded) -> fullDetailsExpanded = expanded);
        content.getChildren().add(full);

        userDetailsHolder.setContent(content);
        // The events screen fades its details in; this panel is rebuilt on every
        // action, so it slides instead and the two screens stay tellable apart.
        AnimationManager.slideIn(content);

        buildActions(user, event);
    }

    /**
     * Every action a user could take on this event is shown. Ones that are not
     * allowed right now are disabled and say why, rather than silently missing.
     */
    private void buildActions(UserDTO user, EventDTO event) {
        boolean isMarketMaker = event.getMarketMakerName().equals(user.getName());
        String status = event.getStatusLabel();
        boolean notStarted = "Not started".equals(status);
        boolean active = "Active".equals(status);
        boolean closed = "Closed".equals(status);

        if (closed) {
            actionBar.getChildren().add(new Label(
                    "This event is closed. Its final result is shown below."));
            return;
        }

        double opening = event.getRequiredOpeningFunds();
        boolean canAfford = user.getBalance() + 1e-9 >= opening;
        Button open = new Button("Open event");
        open.setDisable(user.isBlocked() || !isMarketMaker || !notStarted || !canAfford);
        open.setTooltip(new Tooltip(openReason(user, event, isMarketMaker, notStarted, canAfford, opening)));
        open.setOnAction(e -> confirmOpen(user, event, opening));
        actionBar.getChildren().add(open);

        Button close = new Button("Close event...");
        close.setDisable(user.isBlocked() || !isMarketMaker || !active);
        close.setTooltip(new Tooltip(user.isBlocked()
                ? user.getName() + " is blocked and cannot act."
                : !isMarketMaker
                        ? "Only " + event.getMarketMakerName() + " can close this event."
                        : !active ? "The event has not been opened yet."
                                  : "Decide the winning option and pay the winners."));
        close.setOnAction(e -> TradeForms.closeEvent(engine, event, user.getName(), this::runClose));
        actionBar.getChildren().add(close);

        Button trade = new Button(event.isOrderBook() ? "Place order..." : "Buy shares...");
        trade.setDisable(user.isBlocked() || !active);
        trade.setTooltip(new Tooltip(user.isBlocked()
                ? user.getName() + " is blocked and cannot act."
                : !active ? "Trading opens once " + event.getMarketMakerName() + " starts the event."
                          : "Take part in this event."));
        trade.setOnAction(e -> {
            if (event.isOrderBook()) {
                TradeForms.placeOrder(engine, event, user.getName(), this::run);
            } else {
                TradeForms.buyLmsr(engine, event, user.getName(), this::run);
            }
        });
        actionBar.getChildren().add(trade);
    }

    private String openReason(UserDTO user, EventDTO event, boolean isMarketMaker,
                              boolean notStarted, boolean canAfford, double opening) {
        if (user.isBlocked()) {
            return user.getName() + " is blocked and cannot act.";
        }
        if (!isMarketMaker) {
            return "Only " + event.getMarketMakerName() + " can open this event.";
        }
        if (!notStarted) {
            return "This event has already been opened.";
        }
        if (!canAfford) {
            return "Opening costs " + Format.money(opening) + " but "
                    + user.getName() + " has " + Format.money(user.getBalance()) + ".";
        }
        return "Pay " + Format.money(opening) + " to start trading in this event.";
    }

    private void confirmOpen(UserDTO user, EventDTO event, double opening) {
        boolean go = Dialogs.confirm("Open '" + event.getName() + "'?",
                user.getName() + " will pay " + Format.money(opening)
                        + " into the event account to start it. This cannot be undone.");
        if (!go) {
            return;
        }
        run(() -> {
            engine.openEvent(event.getId(), user.getName());
            Dialogs.info("Event opened",
                    "'" + event.getName() + "' is now active and open for trading.");
        });
    }

    @FXML
    private void onCreateEvent() {
        UserDTO user = usersTable.getSelectionModel().getSelectedItem();
        if (user == null) {
            Dialogs.info("Choose a user", "Select the user who should own the new event first.");
            return;
        }
        NewEventRequestDTO request = CreateEventDialog.show(user.getName());
        if (request != null) {
            run(() -> {
                EventDTO created = engine.createEvent(request, user.getName());
                Dialogs.info("Event created",
                        "'" + created.getName() + "' was created with " + user.getName()
                                + " as its market maker. It still needs to be opened before trading.");
            });
        }
    }

    /** Runs an engine action, reports any refusal, and refreshes the whole window. */
    public void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Dialogs.error("Action refused", String.valueOf(e.getMessage()));
        }
        onChanged.run();
    }

    /** Closing reports what it actually did before the window reloads. */
    private void runClose(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Dialogs.error("Action refused", String.valueOf(e.getMessage()));
        }
        onChanged.run();
    }

    /** Builds the message shown after an event has been closed. */
    public static String describeClose(CloseResultDTO result) {
        StringBuilder message = new StringBuilder();
        message.append("'").append(result.getWinningOptionName()).append("' won.\n\n");
        message.append("Winners paid: ").append(result.getWinnersPaid()).append('\n');
        message.append("Total paid out: ").append(Format.money(result.getTotalPaidOut())).append('\n');
        if (result.getCommissionCollected() > 0) {
            message.append("Commission collected: ")
                    .append(Format.money(result.getCommissionCollected())).append('\n');
        }
        if (result.getReturnedToMarketMaker() > 0) {
            message.append("Returned to the market maker: ")
                    .append(Format.money(result.getReturnedToMarketMaker())).append('\n');
        }
        if (result.getCancelledOrders() > 0) {
            message.append(result.getCancelledOrders())
                    .append(result.getCancelledOrders() == 1
                            ? " resting order was cancelled." : " resting orders were cancelled.");
        }
        return message.toString().trim();
    }
}
