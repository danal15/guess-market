package controller;

import anim.AnimationManager;
import engine.api.GMEngine;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
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
    @FXML private Tab eventsTab;
    @FXML private Tab usersTab;
    @FXML private ChoiceBox<SkinManager.Skin> skinChoice;
    @FXML private CheckBox animationsCheck;

    // Injected by FXMLLoader from the fx:id of each fx:include, plus "Controller".
    @FXML private EventsTabController eventsPaneController;
    @FXML private UsersTabController usersPaneController;

    private GMEngine engine;

    @FXML
    private void initialize() {
        progressBar.setVisible(false);
        progressLabel.setText("");

        skinChoice.getItems().addAll(SkinManager.Skin.values());
        skinChoice.getSelectionModel().select(SkinManager.Skin.DEFAULT);
        skinChoice.setOnAction(e -> applySkin());

        animationsCheck.setSelected(false);
        AnimationManager.setEnabled(false);
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
        Window window = loadButton.getScene() == null ? null : loadButton.getScene().getWindow();
        File chosen = chooser.showOpenDialog(window);
        if (chosen == null) {
            return;
        }
        startLoad(chosen.getAbsolutePath());
    }

    private void startLoad(String path) {
        LoadFileTask loadTask = new LoadFileTask(engine, path);

        progressBar.setVisible(true);
        progressBar.progressProperty().bind(loadTask.progressProperty());
        progressLabel.textProperty().bind(loadTask.messageProperty());
        setBusy(true);

        loadTask.setOnSucceeded(e -> {
            unbindProgress();
            setBusy(false);
            filePathField.setText(engine.getLoadedFilePath());
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

    private void unbindProgress() {
        progressBar.progressProperty().unbind();
        progressLabel.textProperty().unbind();
        progressBar.setVisible(false);
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
