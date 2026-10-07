package br.com.planned.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.planned.api.entity.Complexity;

/** Read through {@code LookupService}'s cache, not directly. */
public interface ComplexityRepository extends JpaRepository<Complexity, Integer> {
}
