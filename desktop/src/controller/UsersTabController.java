package controller;

import engine.api.GMEngine;
import engine.api.dto.EventDTO;
import engine.api.dto.NewEventRequestDTO;
import engine.api.dto.UserDTO;
import engine.api.dto.UserEventInvolvementDTO;
import engine.model.OrderSide;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import util.Dialogs;
import util.Format;
import view.CreateEventDialog;
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

    @FXML
    private void initialize() {
        buildUsersTable();
        buildUserEventsTable();
        userDetailsHolder.setContent(new Label("Select a user and one of their events."));
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
        TableColumn<UserDTO, String> name = new TableColumn<>("User");
        name.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        TableColumn<UserDTO, String> balance = new TableColumn<>("Balance");
        balance.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                Format.money(c.getValue().getBalance())));
        TableColumn<UserDTO, String> mm = new TableColumn<>("MM");
        mm.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().isMarketMaker() ? "yes" : ""));
        TableColumn<UserDTO, String> blocked = new TableColumn<>("Blocked");
        blocked.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().isBlocked() ? "yes" : ""));

        usersTable.getColumns().addAll(java.util.List.of(name, balance, mm, blocked));
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        usersTable.setPlaceholder(new Label("No users loaded."));
        usersTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> onUserSelected(selected));
    }

    private void buildUserEventsTable() {
        TableColumn<EventDTO, String> name = new TableColumn<>("Event");
        name.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        TableColumn<EventDTO, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getStatusLabel()));
        TableColumn<EventDTO, String> method = new TableColumn<>("Type");
        method.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getMethodLabel()));
        TableColumn<EventDTO, String> role = new TableColumn<>("Role");
        role.setCellValueFactory(c -> {
            UserDTO user = usersTable.getSelectionModel().getSelectedItem();
            boolean isMm = user != null && c.getValue().getMarketMakerName().equals(user.getName());
            return new javafx.beans.property.SimpleStringProperty(isMm ? "market maker" : "participant");
        });

        userEventsTable.getColumns().addAll(java.util.List.of(name, status, method, role));
        userEventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        userEventsTable.setPlaceholder(new Label("This user is not involved in any event yet."));
        userEventsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> onEventSelected(selected));
    }

    public void refresh() {
        if (engine == null || !engine.isLoaded()) {
            usersTable.setItems(FXCollections.observableArrayList());
            userEventsTable.setItems(FXCollections.observableArrayList());
            userDetailsHolder.setContent(new Label("Load a market file to see users."));
            balanceLabel.setText("Balance: -");
            blockedLabel.setText("");
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
            blockedLabel.setText("");
            userEventsTable.setItems(FXCollections.observableArrayList());
            userDetailsHolder.setContent(new Label("Select a user."));
            createEventButton.setDisable(true);
            return;
        }
        balanceLabel.setText("Balance: " + Format.money(user.getBalance()));
        blockedLabel.setText(user.isBlocked() ? "BLOCKED" : "");
        createEventButton.setDisable(user.isBlocked());

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

    private void onEventSelected(EventDTO event) {
        actionBar.getChildren().clear();
        UserDTO user = usersTable.getSelectionModel().getSelectedItem();
        if (user == null || event == null) {
            userDetailsHolder.setContent(new Label("Select one of this user's events."));
            return;
        }

        VBox content = new VBox(10);
        UserEventInvolvementDTO involvement = engine.getUserInvolvement(user.getName(), event.getId());
        content.getChildren().add(UserInvolvementView.build(involvement));

        if (event.isOrderBook()) {
            content.getChildren().add(EventDetailView.buildOrderBook(
                    engine.getOrderBookEventState(event.getId())));
        } else {
            content.getChildren().add(EventDetailView.buildLmsr(
                    engine.getLmsrEventState(event.getId())));
        }
        userDetailsHolder.setContent(content);

        buildActions(user, event);
    }

    private void buildActions(UserDTO user, EventDTO event) {
        boolean isMarketMaker = event.getMarketMakerName().equals(user.getName());
        String status = event.getStatusLabel();

        if (user.isBlocked()) {
            actionBar.getChildren().add(new Label(
                    "This user is blocked after going into a negative balance and can no longer act."));
            return;
        }

        if (isMarketMaker && "Not started".equals(status)) {
            Button open = new Button("Open event");
            open.setOnAction(e -> run(() -> {
                engine.openEvent(event.getId(), user.getName());
                Dialogs.info("Event opened", "'" + event.getName() + "' is now active.");
            }));
            actionBar.getChildren().add(open);
        }

        if (isMarketMaker && "Active".equals(status)) {
            Button close = new Button("Close event...");
            close.setOnAction(e -> TradeForms.closeEvent(engine, event, user.getName(), this::run));
            actionBar.getChildren().add(close);
        }

        if ("Active".equals(status)) {
            if (event.isOrderBook()) {
                Button order = new Button("Place order...");
                order.setOnAction(e -> TradeForms.placeOrder(engine, event, user.getName(), this::run));
                actionBar.getChildren().add(order);
            } else {
                Button buy = new Button("Buy shares...");
                buy.setOnAction(e -> TradeForms.buyLmsr(engine, event, user.getName(), this::run));
                actionBar.getChildren().add(buy);
            }
        }

        if (actionBar.getChildren().isEmpty()) {
            String reason = "Not started".equals(status)
                    ? "Waiting for " + event.getMarketMakerName() + " to open this event."
                    : "This event is closed.";
            actionBar.getChildren().add(new Label(reason));
        }
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
                        "'" + created.getName() + "' was created with " + user.getName() + " as its market maker.");
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
}
