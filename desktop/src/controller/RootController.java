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

    @FXML private Button loadButton;
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

    @FXML
    private void onLoadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a Guess Market file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File startIn = lastDirectory != null ? lastDirectory : defaultDirectory();
        if (startIn != null && startIn.isDirectory()) {
            chooser.setInitialDirectory(startIn);
        }
        Window window = loadButton.getScene() == null ? null : loadButton.getScene().getWindow();
        File chosen = chooser.showOpenDialog(window);
        if (chosen == null) {
            return;
        }
        lastDirectory = chosen.getParentFile();
        startLoad(chosen.getAbsolutePath());
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
        tabPane.setDisable(busy);
    }

    public void refreshAll() {
        eventsPaneController.refresh();
        usersPaneController.refresh();
    }
}
