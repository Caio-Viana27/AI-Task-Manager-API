package br.com.planned.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.planned.api.entity.Priority;

/** Read through {@code LookupService}'s cache, not directly. */
public interface PriorityRepository extends JpaRepository<Priority, Integer> {
}
