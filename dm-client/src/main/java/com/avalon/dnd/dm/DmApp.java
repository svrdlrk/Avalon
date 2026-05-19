package com.avalon.dnd.dm;

import com.avalon.dnd.dm.ui.MainStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.concurrent.atomic.AtomicBoolean;

public class DmApp extends Application {

    private final AtomicBoolean launcherClosedNotified = new AtomicBoolean(false);
    private MainStage mainStage;

    @Override
    public void start(Stage primaryStage) {
        mainStage = new MainStage(primaryStage);
        mainStage.show();
        primaryStage.setOnCloseRequest(event -> {
            disposeMainStage();
            notifyLauncherClosed();
            Platform.exit();
            System.exit(0);
        });
        System.out.println("✅ DM-клиент запущен через MainStage");
    }

    @Override
    public void stop() {
        disposeMainStage();
        notifyLauncherClosed();
        System.out.println("DM-клиент закрыт");
    }

    private void disposeMainStage() {
        if (mainStage != null) {
            mainStage.dispose();
            mainStage = null;
        }
    }

    private void notifyLauncherClosed() {
        if (!launcherClosedNotified.compareAndSet(false, true)) {
            return;
        }

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
