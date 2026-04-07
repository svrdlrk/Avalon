package com.avalon.dnd.dm;

import com.avalon.dnd.dm.ui.MainStage;
import javafx.application.Application;
import javafx.stage.Stage;

public class DmApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Вызываем твой крутой интерфейс
        new MainStage(primaryStage).show();
        System.out.println("✅ DM-клиент запущен через MainStage");
    }

    @Override
    public void stop() {
        System.out.println("DM-клиент закрыт");
    }
}