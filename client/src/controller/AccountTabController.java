package controller;

import anim.AnimationManager;
import engine.api.dto.EventDTO;
import engine.api.dto.MovementDTO;
import engine.api.dto.NewEventRequestDTO;
import engine.api.dto.SnapshotDTO;
import engine.api.dto.UserDTO;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import net.MarketClient;
import util.Dialogs;
import util.Format;
import util.Tables;
import view.BalanceChartView;
import view.CreateEventDialog;
import view.EventDetailView;
import view.TradeForms;
import view.UserInvolvementView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This user's own screen: what their account holds, where it has been, and the
 * actions they are allowed to take right now.
 *
 * Only this user's own figures are here. Of everybody else it shows no more than
 * the exercise asks for - a name, a balance, and whether they run any events -
 * while the public side of trading, the order books and who is holding what,
 * stays on the Events tab where it belongs.
 */
public class AccountTabController {

    @FXML private Button uploadButton;
    @FXML private TextField filePathField;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    @FXML private Label ownBalanceLabel;
    @FXML private Button loadFundsButton;
    @FXML private TableView<MovementDTO> movementsTable;
    @FXML private TitledPane chartPane;
    @FXML private TableView<UserDTO> usersTable;

    @FXML private TableView<EventDTO> eventsTable;
    @FXML private HBox actionBar;
    @FXML private Button createEventButton;
    @FXML private ScrollPane detailsHolder;

    private MarketClient client;
    private Runnable onChanged = () -> { };

    private SnapshotDTO latest;
    private File lastDirectory;

    /** Remembered so acting on an event does not fold the panel away again. */
    private boolean fullDetailsExpanded;
    private boolean chartExpanded;

    @FXML
    private void initialize() {
        buildMovementsTable();
        buildUsersTable();
        buildEventsTable();

        progressBar.setVisible(false);
        progressBar.managedProperty().bind(progressBar.visibleProperty());
        progressLabel.setVisible(false);
        progressLabel.managedProperty().bind(progressLabel.visibleProperty());

        filePathField.setTooltip(new Tooltip("The last events file you uploaded."));
        detailsHolder.setContent(new Label("Select one of the events above to take part in it."));

        chartPane.setExpanded(false);
        chartPane.expandedProperty().addListener((observable, old, expanded) -> chartExpanded = expanded);
    }

    public void start(MarketClient client, Runnable onChanged) {
        this.client = client;
        this.onChanged = onChanged;
    }

    // ---------- tables ----------

    private void buildMovementsTable() {
        movementsTable.getColumns().addAll(List.of(
                Tables.count("#", movement -> (long) movement.getIndex()),
                Tables.text("What happened", MovementDTO::getReason),
                Tables.text("Event", movement ->
                        movement.getEventName() == null ? "" : movement.getEventName()),
                Tables.text("Amount", movement -> Format.signed(movement.getAmount())),
                Tables.money("Balance after", MovementDTO::getBalanceAfter)));
        movementsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movementsTable.setPlaceholder(new Label("Nothing has moved in this account yet."));
    }

    private void buildUsersTable() {
        usersTable.getColumns().addAll(List.of(
                Tables.text("User", UserDTO::getName),
                Tables.money("Balance", UserDTO::getBalance),
                Tables.yesNo("Market maker", UserDTO::isMarketMaker)));
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        usersTable.setPlaceholder(new Label("Nobody else is logged in."));
    }

    private void buildEventsTable() {
        eventsTable.getColumns().addAll(List.of(
                Tables.text("Event", EventDTO::getName),
                Tables.text("Status", EventDTO::getStatusLabel),
                Tables.text("Method", EventDTO::getMethodLabel),
                Tables.text("Your role", this::roleIn)));
        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventsTable.setPlaceholder(new Label("No events yet. Upload a file to start some."));
        eventsTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, old, selected) -> showEvent(selected));
    }

    /**
     * Read from the picture already in hand rather than asked of the server, which
     * matters because a cell is rebuilt for every visible row on every poll.
     */
    private String roleIn(EventDTO event) {
        if (latest == null) {
            return "";
        }
        if (event.getMarketMakerName().equals(latest.getUser().getName())) {
            return "market maker";
        }
        return latest.getParticipatingEventIds().contains(event.getId())
                ? "taking part" : "not involved yet";
    }

    // ---------- the poll arriving ----------

    public Integer getSelectedEventId() {
        EventDTO selected = eventsTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    public void apply(SnapshotDTO snapshot) {
        this.latest = snapshot;
        UserDTO me = snapshot.getUser();

        ownBalanceLabel.setText("Balance: " + Format.money(me.getBalance()));
        movementsTable.setItems(FXCollections.observableArrayList(snapshot.getMovements()));

        List<UserDTO> others = new ArrayList<>();
        for (UserDTO user : snapshot.getUsers()) {
            if (!user.getName().equals(me.getName())) {
                others.add(user);
            }
        }
        usersTable.setItems(FXCollections.observableArrayList(others));

        if (chartPane.isExpanded()) {
            chartPane.setContent(BalanceChartView.build(me.getName(), snapshot.getBalanceHistory()));
        }
        chartPane.setExpanded(chartExpanded);

        createEventButton.setDisable(me.isBlocked());
        createEventButton.setTooltip(new Tooltip(me.isBlocked()
                ? "This account is blocked and cannot create events."
                : "Create a new event with you as its market maker."));
        loadFundsButton.setTooltip(new Tooltip(me.isBlocked()
                ? "Load funds to cover the account and lift the block."
                : "Put money into your own account."));

        showEvents();
        showEvent(eventsTable.getSelectionModel().getSelectedItem());
    }

    private void showEvents() {
        EventDTO previous = eventsTable.getSelectionModel().getSelectedItem();
        eventsTable.setItems(FXCollections.observableArrayList(latest.getEvents()));
        if (previous != null) {
            for (EventDTO candidate : latest.getEvents()) {
                if (candidate.getId() == previous.getId()) {
                    eventsTable.getSelectionModel().select(candidate);
                    return;
                }
            }
        }
    }

    private void showEvent(EventDTO event) {
        actionBar.getChildren().clear();
        if (event == null || latest == null) {
            detailsHolder.setContent(new Label("Select one of the events above to take part in it."));
            return;
        }
        if (latest.getInvolvement() == null
                || latest.getInvolvement().getEventId() != event.getId()) {
            detailsHolder.setContent(new Label("Loading '" + event.getName() + "'..."));
            buildActions(latest.getUser(), event);
            return;
        }

        VBox content = new VBox(10);
        content.getChildren().add(UserInvolvementView.build(latest.getInvolvement()));

        Node full = latest.getOrderBookState() != null
                ? EventDetailView.buildOrderBook(latest.getOrderBookState())
                : latest.getLmsrState() != null
                        ? EventDetailView.buildLmsr(latest.getLmsrState())
                        : new Label("");
        TitledPane fullPane = new TitledPane("Full event details", full);
        fullPane.setExpanded(fullDetailsExpanded);
        fullPane.setAnimated(false);
        fullPane.expandedProperty()
                .addListener((observable, old, expanded) -> fullDetailsExpanded = expanded);
        content.getChildren().add(fullPane);

        detailsHolder.setContent(content);
        AnimationManager.slideIn(content);
        buildActions(latest.getUser(), event);
    }

    /**
     * Every action this user could take on this event is shown. Ones that are not
     * allowed right now are disabled and say why, rather than silently missing.
     */
    private void buildActions(UserDTO user, EventDTO event) {
        boolean isMarketMaker = event.getMarketMakerName().equals(user.getName());
        String status = event.getStatusLabel();
        boolean notStarted = "Not started".equals(status);
        boolean active = "Active".equals(status);

        if ("Closed".equals(status)) {
            actionBar.getChildren().add(new Label(
                    "This event is closed. Its final result is shown below."));
            return;
        }

        double opening = event.getRequiredOpeningFunds();
        boolean canAfford = user.getBalance() + 1e-9 >= opening;
        Button open = new Button("Open event");
        open.setDisable(user.isBlocked() || !isMarketMaker || !notStarted || !canAfford);
        open.setTooltip(new Tooltip(openReason(user, event, isMarketMaker, notStarted, canAfford, opening)));
        open.setOnAction(action -> confirmOpen(user, event, opening));
        actionBar.getChildren().add(open);

        Button close = new Button("Close event...");
        close.setDisable(user.isBlocked() || !isMarketMaker || !active);
        close.setTooltip(new Tooltip(user.isBlocked()
                ? "This account is blocked and cannot act."
                : !isMarketMaker
                        ? "Only " + event.getMarketMakerName() + " can close this event."
                        : !active ? "The event has not been opened yet."
                                  : "Decide the winning option and pay the winners."));
        close.setOnAction(action ->
                TradeForms.closeEvent(client, event, user.getName(), this::run));
        actionBar.getChildren().add(close);

        Button trade = new Button(event.isOrderBook() ? "Place order..." : "Buy shares...");
        trade.setDisable(user.isBlocked() || !active);
        trade.setTooltip(new Tooltip(user.isBlocked()
                ? "This account is blocked and cannot act."
                : !active ? "Trading opens once " + event.getMarketMakerName() + " starts the event."
                          : "Take part in this event."));
        trade.setOnAction(action -> {
            if (event.isOrderBook()) {
                TradeForms.placeOrder(client, event, user.getName(), this::run);
            } else {
                TradeForms.buyLmsr(client, event, user.getName(), this::run);
            }
        });
        actionBar.getChildren().add(trade);
    }

    private String openReason(UserDTO user, EventDTO event, boolean isMarketMaker,
                              boolean notStarted, boolean canAfford, double opening) {
        if (user.isBlocked()) {
            return "This account is blocked and cannot act.";
        }
        if (!isMarketMaker) {
            return "Only " + event.getMarketMakerName() + " can open this event.";
        }
        if (!notStarted) {
            return "This event has already been opened.";
        }
        if (!canAfford) {
            return "Opening costs " + Format.money(opening) + " but the account holds "
                    + Format.money(user.getBalance()) + ". Load funds first.";
        }
        return "Pay " + Format.money(opening) + " to start trading in this event.";
    }

    private void confirmOpen(UserDTO user, EventDTO event, double opening) {
        boolean go = Dialogs.confirm("Open '" + event.getName() + "'?",
                "You will pay " + Format.money(opening)
                        + " into the event account to start it. This cannot be undone.");
        if (!go) {
            return;
        }
        run(() -> {
            client.openEvent(event.getId(), user.getName());
            Dialogs.info("Event opened",
                    "'" + event.getName() + "' is now active and open for trading.");
        });
    }

    // ---------- the account itself ----------

    @FXML
    private void onLoadFunds() {
        TextInputDialog dialog = new TextInputDialog("100");
        dialog.setTitle("Load funds");
        dialog.setHeaderText(null);
        dialog.setContentText("Amount to put into your account:");
        util.DialogChrome.apply(dialog, "How much would you like to load?", null);

        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(answer.get().trim());
        } catch (NumberFormatException e) {
            Dialogs.error("That is not an amount", "'" + answer.get() + "' is not a number.");
            return;
        }
        run(() -> {
            UserDTO after = client.loadFunds(client.getUserName(), amount);
            Dialogs.info("Funds loaded", Format.money(amount)
                    + " went into your account, which now holds "
                    + Format.money(after.getBalance()) + ".");
        });
    }

    @FXML
    private void onCreateEvent() {
        NewEventRequestDTO request = CreateEventDialog.show(client.getUserName());
        if (request == null) {
            return;
        }
        run(() -> {
            EventDTO created = client.createEvent(request, client.getUserName());
            Dialogs.info("Event created", "'" + created.getName()
                    + "' was created with you as its market maker."
                    + " It still needs to be opened before trading.");
        });
    }

    // ---------- uploading a file of events ----------

    /**
     * Reads the chosen file here and sends its bytes to the server. The server is
     * never given a path, and never writes the file down: the exercise warns that
     * it may not be allowed to write at all.
     */
    @FXML
    private void onUpload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an events file to upload");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Exercise 3 events files", "*.xml"));
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        File chosen = chooser.showOpenDialog(windowOf(uploadButton));
        if (chosen == null) {
            return;
        }
        lastDirectory = chosen.getParentFile();

        if (!chosen.getName().toLowerCase().endsWith(".xml")) {
            Dialogs.error("That file cannot be uploaded",
                    "An events file has to end in .xml, and '" + chosen.getName() + "' does not.");
            return;
        }

        byte[] content;
        try {
            content = Files.readAllBytes(chosen.toPath());
        } catch (IOException e) {
            Dialogs.error("The file could not be read", String.valueOf(e.getMessage()));
            return;
        }
        if (content.length == 0) {
            Dialogs.error("That file is empty", "'" + chosen.getName() + "' has nothing in it.");
            return;
        }

        startUpload(chosen.getName(), content);
    }

    /**
     * The upload really does take time now, since it goes to the server, so the
     * progress indicator reports something real rather than a staged pause.
     */
    private void startUpload(String fileName, byte[] content) {
        Task<MarketClient.UploadResult> upload = new Task<>() {
            @Override
            protected MarketClient.UploadResult call() {
                updateMessage("Sending " + fileName + "...");
                return client.upload(fileName, content);
            }
        };

        progressBar.setVisible(true);
        progressLabel.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.textProperty().bind(upload.messageProperty());
        setBusy(true);

        upload.setOnSucceeded(event -> {
            finishUpload();
            MarketClient.UploadResult result = upload.getValue();
            filePathField.setText(fileName);
            AnimationManager.pulse(filePathField);
            Dialogs.info("File loaded", describe(result));
            onChanged.run();
        });
        upload.setOnFailed(event -> {
            finishUpload();
            Throwable error = upload.getException();
            Dialogs.error("The file was not loaded",
                    error == null ? "Unknown problem." : String.valueOf(error.getMessage()));
        });

        Thread thread = new Thread(upload, "upload-events-file");
        thread.setDaemon(true);
        thread.start();
    }

    private String describe(MarketClient.UploadResult result) {
        List<String> names = result.getEventNames();
        StringBuilder message = new StringBuilder();
        message.append(names.size() == 1 ? "1 event was added" : names.size() + " events were added");
        message.append(", with you as the market maker:\n\n");
        for (String name : names) {
            message.append("  - ").append(name).append('\n');
        }
        message.append("\nEach one still has to be opened before anybody can trade in it.");
        return message.toString();
    }

    private void finishUpload() {
        progressLabel.textProperty().unbind();
        progressLabel.setText("");
        progressBar.setVisible(false);
        progressLabel.setVisible(false);
        progressBar.setProgress(0);
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        uploadButton.setDisable(busy);
        createEventButton.setDisable(busy);
        loadFundsButton.setDisable(busy);
    }

    private Window windowOf(Button button) {
        return button.getScene() == null ? null : button.getScene().getWindow();
    }

    /** Runs an action, reports a refusal, then asks the server for a fresh picture. */
    public void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Dialogs.error("Action refused", String.valueOf(e.getMessage()));
        }
        onChanged.run();
    }
}
