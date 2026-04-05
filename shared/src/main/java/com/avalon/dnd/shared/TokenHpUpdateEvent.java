package com.avalon.dnd.shared;

public class TokenHpUpdateEvent {

    private String tokenId;
    private int hp;
    private int maxHp;

    public TokenHpUpdateEvent() {}

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }
}
