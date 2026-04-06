package com.avalon.dnd.dm;

import com.avalon.dnd.dm.ui.DmMainController;
import com.avalon.dnd.dm.ui.MainStage;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DmApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DmMain.fxml"));
        Parent root = loader.load();

        // Загружаем контроллер
        DmMainController controller = loader.getController();

        primaryStage.setTitle("Avalon — DM Tool");
        primaryStage.setScene(new Scene(root, 1400, 850));
        primaryStage.setResizable(true);
        primaryStage.show();

        System.out.println("✅ DM-клиент запущен с FXML-интерфейсом");
    }

    @Override
    public void stop() {
        System.out.println("DM-клиент закрыт");
    }
}