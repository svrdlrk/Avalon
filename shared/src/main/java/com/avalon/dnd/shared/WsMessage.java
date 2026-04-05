package com.avalon.dnd.shared;

public class WsMessage<T> {

    private WsEventType type;
    private String sessionId;
    private long version;
    private T payload;

    public WsMessage() {}

    public WsMessage(WsEventType type, String sessionId, long version, T payload) {
        this.type = type;
        this.sessionId = sessionId;
        this.version = version;
        this.payload = payload;
    }

    public WsEventType getType() {
        return type;
    }

    public void setType(WsEventType type) {
        this.type = type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getVersion() { return version; }

    public void setVersion(long version) {
        this.version = version;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }
}