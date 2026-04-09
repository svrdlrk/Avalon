package com.avalon.dnd.shared;

import java.util.List;

/**
 * Запрос от DM-клиента на обновление/публикацию инициативы.
 */
public class InitiativeUpdateRequest {

    /** Полный список в нужном порядке (уже отсортированный по инициативе DM-ом). */
    private List<InitiativeStateDto.InitiativeEntry> entries;
    /** Индекс текущего хода (0-based). */
    private int currentIndex;

    public InitiativeUpdateRequest() {}

    public List<InitiativeStateDto.InitiativeEntry> getEntries()      { return entries; }
    public int                                      getCurrentIndex() { return currentIndex; }
    public void setEntries(List<InitiativeStateDto.InitiativeEntry> e) { this.entries = e; }
    public void setCurrentIndex(int i)                                 { this.currentIndex = i; }
}