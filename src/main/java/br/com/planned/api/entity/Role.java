package br.com.planned.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A seeded lookup row of {@code ROLE} ({@code USER}, {@code ADMIN}). Look it up by name, never by id. */
@Entity
@Table(name = "role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

	public static final String USER = "USER";
	public static final String ADMIN = "ADMIN";

	@Id
	private Integer id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;
}
