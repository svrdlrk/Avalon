package com.avalon.dnd.shared;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JoinSessionRequestDto {

    private String sessionId;
    private String playerName;
    private String joinNonce;
    private String dmSecret;
    private String projectorToken;

    @JsonProperty("isDm")
    private boolean isDm;

    @JsonProperty("isObserver")
    private boolean isObserver;

    public JoinSessionRequestDto() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getJoinNonce() { return joinNonce; }
    public void setJoinNonce(String joinNonce) { this.joinNonce = joinNonce; }

    public String getDmSecret() { return dmSecret; }
    public void setDmSecret(String dmSecret) { this.dmSecret = dmSecret; }

    public String getProjectorToken() { return projectorToken; }
    public void setProjectorToken(String projectorToken) { this.projectorToken = projectorToken; }

    public boolean isDm() { return isDm; }
    public void setDm(boolean dm) { isDm = dm; }

    public boolean isObserver() { return isObserver; }
    public void setObserver(boolean observer) { isObserver = observer; }
}
