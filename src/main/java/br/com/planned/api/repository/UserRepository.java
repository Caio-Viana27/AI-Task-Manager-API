package br.com.planned.api.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.planned.api.entity.User;

/**
 * Emails are stored lowercase (wave 1, D4), so callers pass the normalized email and the
 * {@code UNIQUE (EMAIL)} index serves these lookups. No {@code IgnoreCase} queries.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
