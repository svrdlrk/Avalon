package com.avalon.dnd.shared;

/**
 * Immutable initiative queue entry shared between DM UI helpers and the DM stage.
 */
public record InitEntry(String id, String name, int initiative) {}
