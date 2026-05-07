package com.avalon.dnd.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Pending DM approval for turning a private visibility discovery into shared party knowledge.
 */
public class VisibilityShareSuggestionDto {

    private String suggestionId;
    private List<String> playerIds = new ArrayList<>();
    private String reason;
    private boolean autoSuggested;
    private String trigger;

    public VisibilityShareSuggestionDto() {}

    public VisibilityShareSuggestionDto(String suggestionId, List<String> playerIds, String reason, boolean autoSuggested) {
        this(suggestionId, playerIds, reason, autoSuggested, null);
    }

    public VisibilityShareSuggestionDto(String suggestionId, List<String> playerIds, String reason, boolean autoSuggested, String trigger) {
        this.suggestionId = suggestionId;
        setPlayerIds(playerIds);
        this.reason = reason;
        this.autoSuggested = autoSuggested;
        this.trigger = trigger;
    }

    public String getSuggestionId() { return suggestionId; }
    public void setSuggestionId(String suggestionId) { this.suggestionId = suggestionId; }

    public List<String> getPlayerIds() { return playerIds; }
    public void setPlayerIds(List<String> playerIds) {
        this.playerIds.clear();
        if (playerIds != null) this.playerIds.addAll(playerIds);
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isAutoSuggested() { return autoSuggested; }
    public void setAutoSuggested(boolean autoSuggested) { this.autoSuggested = autoSuggested; }

    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
}
