package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;

public class RootController {

    @FXML private Button loadButton;
    @FXML private TextField filePathField;
    @FXML private ProgressBar progressBar;

    @FXML
    private void initialize() {
        progressBar.setVisible(false);
    }

    @FXML
    private void onLoadFile() {
        // Wired up once the engine refactor lands: FileChooser -> LoadFileTask -> refresh tabs.
    }
}
