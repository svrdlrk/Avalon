package com.avalon.dnd.server.model;

public class Player {

    private String id;
    private String name;
    private String sessionId;
    private Role role;

    public Player() {}

    public Player(String id, String name, String sessionId, Role role) {
        this.id = id;
        this.name = name;
        this.sessionId = sessionId;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Role getRole() {
        return role;
    }

    public void setName(String name) {
        this.name = name;
    }
}