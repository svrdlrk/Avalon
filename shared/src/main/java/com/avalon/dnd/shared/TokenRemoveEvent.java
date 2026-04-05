package com.avalon.dnd.shared;

public class TokenRemoveEvent {

    private String tokenId;

    public TokenRemoveEvent() {}

    public TokenRemoveEvent(String tokenId) {
        this.tokenId = tokenId;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }
}