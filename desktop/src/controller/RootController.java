package controller;

import anim.AnimationManager;
import engine.api.GMEngine;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import skin.SkinManager;
import task.LoadFileTask;
import util.Dialogs;

import java.io.File;

public class RootController {

    private static final String STATE_SUFFIX = ".gmstate";

    @FXML private Button loadButton;
    @FXML private Button saveStateButton;
    @FXML private TextField filePathField;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private TabPane tabPane;
    @FXML private ChoiceBox<SkinManager.Skin> skinChoice;
    @FXML private CheckBox animationsCheck;

    // Injected by FXMLLoader from the fx:id of each fx:include, plus "Controller".
    @FXML private EventsTabController eventsPaneController;
    @FXML private UsersTabController usersPaneController;

    private GMEngine engine;
    private File lastDirectory;

    @FXML
    private void initialize() {
        // Idle progress controls should take no space at all, rather than
        // leaving a permanent gap in the busiest row of the window.
        progressBar.setVisible(false);
        progressBar.managedProperty().bind(progressBar.visibleProperty());
        progressLabel.setVisible(false);
        progressLabel.managedProperty().bind(progressLabel.visibleProperty());

        filePathField.setTooltip(new Tooltip("No file loaded yet."));

        skinChoice.getItems().addAll(SkinManager.Skin.values());
        skinChoice.getSelectionModel().select(SkinManager.Skin.DEFAULT);
        skinChoice.setTooltip(new Tooltip("Change the colours and fonts of the whole window."));
        skinChoice.setOnAction(e -> applySkin());

        animationsCheck.setSelected(false);
        AnimationManager.setEnabled(false);
        animationsCheck.setTooltip(new Tooltip("Short animations when panels change. Off by default."));
        animationsCheck.setOnAction(e -> AnimationManager.setEnabled(animationsCheck.isSelected()));
    }

    public void setEngine(GMEngine engine) {
        this.engine = engine;
        eventsPaneController.setEngine(engine);
        usersPaneController.setEngine(engine);
        usersPaneController.setOnChanged(this::refreshAll);
        refreshAll();
    }

    private void applySkin() {
        if (skinChoice.getScene() != null) {
            SkinManager.apply(skinChoice.getScene(), skinChoice.getValue());
        }
    }

    /**
     * One button for both kinds of file. A market file is read and validated
     * from scratch; a saved market is restored as it was left. Which one it is
     * follows from the name, so there is nothing for the user to choose.
     */
    @FXML
    private void onLoadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a market file or saved progress");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Market files and saved progress",
                        "*.xml", "*" + STATE_SUFFIX),
                new FileChooser.ExtensionFilter("Market files", "*.xml"),
                new FileChooser.ExtensionFilter("Saved progress", "*" + STATE_SUFFIX));
        File startIn = lastDirectory != null ? lastDirectory : defaultDirectory();
        if (startIn != null && startIn.isDirectory()) {
            chooser.setInitialDirectory(startIn);
        }
        File chosen = chooser.showOpenDialog(windowOf(loadButton));
        if (chosen == null) {
            return;
        }
        lastDirectory = chosen.getParentFile();
        openChosenFile(chosen);
    }

    /** Sends the file down whichever path its name calls for. */
    private void openChosenFile(File chosen) {
        String name = chosen.getName().toLowerCase();
        if (name.endsWith(STATE_SUFFIX)) {
            try {
                engine.loadState(withoutSuffix(chosen));
                showLoadedFile(chosen.getAbsolutePath());
                refreshAll();
                AnimationManager.pulse(filePathField);
            } catch (RuntimeException e) {
                Dialogs.error("The saved progress was not loaded", String.valueOf(e.getMessage()));
            }
            return;
        }
        if (name.endsWith(".xml")) {
            startLoad(chosen.getAbsolutePath());
            return;
        }
        Dialogs.error("That file cannot be opened",
                "Choose either a market file ending in .xml or saved progress ending in "
                        + STATE_SUFFIX + ".");
    }

    /**
     * Writes everything that has happened so far to a file. The market is saved
     * whole, so the trades, the resting orders and the closed events all come
     * back exactly as they were left.
     */
    @FXML
    private void onSaveState() {
        if (engine == null || !engine.isLoaded()) {
            Dialogs.error("Nothing to save", "Load a market file before saving progress.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save progress");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Guess Market progress", "*" + STATE_SUFFIX));
        chooser.setInitialFileName("guess-market-progress" + STATE_SUFFIX);
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        File chosen = chooser.showSaveDialog(windowOf(saveStateButton));
        if (chosen == null) {
            return;
        }
        lastDirectory = chosen.getParentFile();
        try {
            engine.saveState(withoutSuffix(chosen));
            Dialogs.info("Progress saved", "The market was saved to " + chosen.getName() + ".");
        } catch (RuntimeException e) {
            Dialogs.error("Could not save", String.valueOf(e.getMessage()));
        }
    }

    /** The engine adds the suffix itself, so it must not be handed one twice. */
    private String withoutSuffix(File file) {
        String path = file.getAbsolutePath();
        return path.endsWith(STATE_SUFFIX)
                ? path.substring(0, path.length() - STATE_SUFFIX.length())
                : path;
    }

    private Window windowOf(Button button) {
        return button.getScene() == null ? null : button.getScene().getWindow();
    }

    /** The samples that ship beside the jar, so the first load has somewhere to start. */
    private File defaultDirectory() {
        File samples = new File(System.getProperty("user.dir"), "sample-files");
        return samples.isDirectory() ? samples : null;
    }

    private void startLoad(String path) {
        LoadFileTask loadTask = new LoadFileTask(engine, path);

        progressBar.setVisible(true);
        progressLabel.setVisible(true);
        progressBar.progressProperty().bind(loadTask.progressProperty());
        progressLabel.textProperty().bind(loadTask.messageProperty());
        setBusy(true);

        loadTask.setOnSucceeded(e -> {
            unbindProgress();
            setBusy(false);
            showLoadedFile(engine.getLoadedFilePath());
            refreshAll();
            AnimationManager.pulse(filePathField);
        });

        loadTask.setOnFailed(e -> {
            unbindProgress();
            setBusy(false);
            Throwable error = loadTask.getException();
            String message = error == null ? "Unknown problem." : String.valueOf(error.getMessage());
            Dialogs.error("The file was not loaded", message);
        });

        Thread thread = new Thread(loadTask, "load-market-file");
        thread.setDaemon(true);
        thread.start();
    }

    /** The name identifies the file; the full path is context, so it goes in a tooltip. */
    private void showLoadedFile(String path) {
        if (path == null) {
            filePathField.setText("");
            filePathField.setTooltip(new Tooltip("No file loaded yet."));
            return;
        }
        filePathField.setText(new File(path).getName());
        filePathField.setTooltip(new Tooltip(path));
    }

    private void unbindProgress() {
        progressBar.progressProperty().unbind();
        progressLabel.textProperty().unbind();
        progressBar.setVisible(false);
        progressLabel.setVisible(false);
        progressLabel.setText("");
    }

    private void setBusy(boolean busy) {
        loadButton.setDisable(busy);
        // Saving or reloading in the middle of a load would work on a market
        // that is being replaced underneath it.
        saveStateButton.setDisable(busy);
        tabPane.setDisable(busy);
    }

    public void refreshAll() {
        eventsPaneController.refresh();
        usersPaneController.refresh();
    }
}
