package com.avalon.dnd.shared;

public class TokenAssignRequest {

    private String tokenId;
    private String ownerId;

    public TokenAssignRequest() {}

    public TokenAssignRequest(String tokenId, String ownerId) {
        this.tokenId = tokenId;
        this.ownerId = ownerId;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
}