package com.avalon.dnd.dm;

import com.avalon.dnd.dm.net.ServerConnection;
import com.avalon.dnd.dm.ui.MainStage;
import javafx.application.Application;
import javafx.stage.Stage;

public class DmApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        new MainStage(primaryStage).show();
    }

    @Override
    public void stop() {
        // закрытие соединения при выходе
        ServerConnection.getInstance().disconnect();
    }
}