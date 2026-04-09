package com.avalon.dnd.shared;

import java.util.List;

/**
 * Состояние очереди инициативы — рассылается всем клиентам при изменении.
 */
public class InitiativeStateDto {

    private List<InitiativeEntry> entries;
    private int currentIndex;

    public InitiativeStateDto() {}

    public InitiativeStateDto(List<InitiativeEntry> entries, int currentIndex) {
        this.entries      = entries;
        this.currentIndex = currentIndex;
    }

    public List<InitiativeEntry> getEntries()   { return entries; }
    public int                   getCurrentIndex() { return currentIndex; }
    public void setEntries(List<InitiativeEntry> entries)      { this.entries = entries; }
    public void setCurrentIndex(int currentIndex)              { this.currentIndex = currentIndex; }

    // ---- nested ----

    public static class InitiativeEntry {
        private String tokenId;
        private String name;
        private int    initiative;

        public InitiativeEntry() {}

        public InitiativeEntry(String tokenId, String name, int initiative) {
            this.tokenId    = tokenId;
            this.name       = name;
            this.initiative = initiative;
        }

        public String getTokenId()    { return tokenId; }
        public String getName()       { return name; }
        public int    getInitiative() { return initiative; }
        public void   setTokenId(String t)    { this.tokenId    = t; }
        public void   setName(String n)       { this.name       = n; }
        public void   setInitiative(int i)    { this.initiative = i; }
    }
}