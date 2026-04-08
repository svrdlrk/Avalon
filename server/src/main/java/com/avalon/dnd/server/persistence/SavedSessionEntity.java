package com.avalon.dnd.server.persistence;

import jakarta.persistence.*;

/**
 * Персистентное хранилище снапшота сессии.
 * Данные сессии сериализуются в JSON и хранятся в одной колонке.
 */
@Entity
@Table(name = "saved_sessions")
public class SavedSessionEntity {

    @Id
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /** Отображаемое имя для выбора при загрузке (например "Dungeon of Doom"). */
    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /** JSON-снапшот всего состояния GameSession. */
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "saved_at", nullable = false)
    private java.time.LocalDateTime savedAt;

    /** Версия на момент сохранения (для информации). */
    @Column(name = "version")
    private long version;

    public SavedSessionEntity() {}

    public SavedSessionEntity(String sessionId, String displayName,
                              String snapshotJson, long version) {
        this.sessionId = sessionId;
        this.displayName = displayName;
        this.snapshotJson = snapshotJson;
        this.version = version;
        this.savedAt = java.time.LocalDateTime.now();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }

    public java.time.LocalDateTime getSavedAt() { return savedAt; }
    public void setSavedAt(java.time.LocalDateTime savedAt) { this.savedAt = savedAt; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}