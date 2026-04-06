package com.avalon.dnd.dm.ui;

import com.avalon.dnd.dm.net.ServerConnection;
import com.avalon.dnd.shared.GridConfig;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;

import java.io.File;

public class DmMainController {

    @FXML private TextField sessionNameField;
    @FXML private Button createSessionBtn;
    @FXML private Button loadMapBtn;
    @FXML private Button addTokenBtn;
    @FXML private Button revealAllBtn;
    @FXML private Pane canvasContainer;
    @FXML private Label statusLabel;

    private ServerConnection connection;

    @FXML
    public void initialize() {
        connection = ServerConnection.getInstance();

        createSessionBtn.setOnAction(e -> createSession());
        loadMapBtn.setOnAction(e -> loadMap());
        addTokenBtn.setOnAction(e -> addToken());
        revealAllBtn.setOnAction(e -> revealAllFog());

        // Подключение DM (5 аргументов — как в твоём ServerConnection)
        connection.connect("ws://localhost:8080", "DungeonMaster", null, true, null);
        statusLabel.setText("Статус: подключено как DM");
    }

    private void createSession() {
        String name = sessionNameField.getText().trim();
        if (name.isEmpty()) name = "Session-" + System.currentTimeMillis();

        GridConfig grid = new GridConfig(40, 30, 40);
        connection.createSession(name, grid);
        statusLabel.setText("✅ Сессия создана: " + name);
    }

    private void loadMap() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(null);
        if (file != null) {
            connection.uploadMap(file);
            statusLabel.setText("✅ Карта загружена: " + file.getName());
        }
    }

    private void addToken() {
        connection.addToken("Новый токен", 10, 10, 100, 100);
        statusLabel.setText("✅ Токен добавлен");
    }

    private void revealAllFog() {
        connection.revealAllFog();
        statusLabel.setText("✅ Туман войны снят полностью");
    }
}