package com.avalon.dnd.shared;

public class VisibilityShareApprovalRequest {
    private String suggestionId;

    public VisibilityShareApprovalRequest() {}

    public VisibilityShareApprovalRequest(String suggestionId) {
        this.suggestionId = suggestionId;
    }

    public String getSuggestionId() { return suggestionId; }
    public void setSuggestionId(String suggestionId) { this.suggestionId = suggestionId; }
}
