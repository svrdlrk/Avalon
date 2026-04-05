package com.avalon.dnd.shared;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JoinSessionRequestDto {

    private String sessionId;
    private String playerName;
    private String joinNonce;

    @JsonProperty("isDm")
    private boolean isDm;

    public JoinSessionRequestDto() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getJoinNonce() { return joinNonce; }
    public void setJoinNonce(String joinNonce) { this.joinNonce = joinNonce; }

    public boolean isDm() { return isDm; }
    public void setDm(boolean dm) { isDm = dm; }
}