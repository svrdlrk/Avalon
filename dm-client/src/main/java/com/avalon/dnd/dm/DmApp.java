package com.avalon.dnd.dm;

import com.avalon.dnd.dm.ui.MainStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class DmApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Вызываем твой крутой интерфейс
        new MainStage(primaryStage).show();
        primaryStage.setOnCloseRequest(event -> {
            notifyLauncherClosed();
            Platform.exit();
            System.exit(0);
        });
        System.out.println("✅ DM-клиент запущен через MainStage");
    }

    @Override
    public void stop() {
        notifyLauncherClosed();
        System.out.println("DM-клиент закрыт");
    }

    private void notifyLauncherClosed() {
        String controlUrl = System.getProperty("avalon.launcher.controlUrl");
        if (controlUrl == null || controlUrl.isBlank()) {
            controlUrl = System.getenv("AVALON_LAUNCHER_CONTROL_URL");
        }
        if (controlUrl == null || controlUrl.isBlank()) {
            return;
        }

        try {
            java.net.URI uri = java.net.URI.create(controlUrl.replaceAll("/+$", "") + "/launcher/client-closed?client=dm");
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(uri)
                    .timeout(java.time.Duration.ofSeconds(2))
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();
            java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(2))
                    .build()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }
}