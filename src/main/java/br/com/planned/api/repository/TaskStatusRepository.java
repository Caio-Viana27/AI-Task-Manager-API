package br.com.planned.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.planned.api.entity.TaskStatus;

/** Read through {@code LookupService}'s cache, not directly. */
public interface TaskStatusRepository extends JpaRepository<TaskStatus, Integer> {
}
