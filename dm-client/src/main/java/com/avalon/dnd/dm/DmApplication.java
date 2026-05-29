package com.avalon.dnd.dm;

import javafx.application.Application;

public class DmApplication {
    public static void main(String[] args) {
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.forceGPU", "false");
        Application.launch(DmApp.class, args);
    }
}