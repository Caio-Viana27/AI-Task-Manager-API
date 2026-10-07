package br.com.planned.api.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.planned.api.entity.Role;
import br.com.planned.api.entity.User;
import br.com.planned.api.support.IntegrationTest;

/** Each test runs in a transaction that is rolled back, so no rows leak into other tests. */
@Transactional
class UserRepositoryTest extends IntegrationTest {

	private static final Instant CREATED_AT = Instant.parse("2026-03-01T12:34:56.123456Z");

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void seededRolesAreFoundByName() {
		assertThat(roleRepository.findByName(Role.USER)).get().extracting(Role::getName).isEqualTo("USER");
		assertThat(roleRepository.findByName(Role.ADMIN)).get().extracting(Role::getName).isEqualTo("ADMIN");
		assertThat(roleRepository.findByName("NOPE")).isEmpty();
	}

	@Test
	void savesAndFindsUserByEmail() {
		Role userRole = roleRepository.findByName(Role.USER).orElseThrow();
		User saved = userRepository.saveAndFlush(new User("Ana", "ana@example.com", "hash", userRole, CREATED_AT));
		UUID id = saved.getId();
		assertThat(id).isNotNull();
		entityManager.clear();

		User found = userRepository.findByEmail("ana@example.com").orElseThrow();

		assertThat(found.getId()).isEqualTo(id);
		assertThat(found.getName()).isEqualTo("Ana");
		assertThat(found.getPassword()).isEqualTo("hash");
		assertThat(found.getRole().getName()).isEqualTo("USER");
		assertThat(found.getCreatedAt()).isEqualTo(CREATED_AT);
		assertThat(found.getUpdatedAt()).isEqualTo(CREATED_AT);
		assertThat(userRepository.existsByEmail("ana@example.com")).isTrue();
		assertThat(userRepository.existsByEmail("other@example.com")).isFalse();
	}

	@Test
	void timestampsAreStoredAsUtc() {
		Role userRole = roleRepository.findByName(Role.USER).orElseThrow();
		UUID id = userRepository.saveAndFlush(new User("Ana", "ana@example.com", "hash", userRole, CREATED_AT)).getId();

		LocalDateTime stored = jdbcTemplate.queryForObject(
				"SELECT CREATED_AT FROM USERS WHERE ID = ?", LocalDateTime.class, id);

		assertThat(stored).isEqualTo(LocalDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC));
	}

	@Test
	void emailsDifferingOnlyInCaseCannotBothBeInserted() {
		int roleId = roleRepository.findByName(Role.USER).orElseThrow().getId();
		String insert = "INSERT INTO USERS (ID, NAME, EMAIL, PASSWORD, ROLE_ID, CREATED_AT, UPDATED_AT) "
				+ "VALUES (?, 'Ana', ?, 'hash', ?, now(), now())";
		jdbcTemplate.update(insert, UUID.randomUUID(), "Ana@x.com", roleId);

		assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), "ana@x.com", roleId))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ux_users_email_lower");
	}
}
