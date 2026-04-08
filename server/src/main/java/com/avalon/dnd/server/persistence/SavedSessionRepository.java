package com.avalon.dnd.server.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedSessionRepository extends JpaRepository<SavedSessionEntity, String> {
    /** Список всех сохранений, отсортированных по дате (новые первыми). */
    List<SavedSessionEntity> findAllByOrderBySavedAtDesc();
}