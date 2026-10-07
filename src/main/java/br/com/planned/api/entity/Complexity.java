package br.com.planned.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A seeded lookup row of {@code COMPLEXITIES}. Look it up by name, never by id. */
@Entity
@Table(name = "complexities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Complexity {

	public static final String EASY = "EASY";
	public static final String MEDIUM = "MEDIUM";
	public static final String HARD = "HARD";

	@Id
	private Integer id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;
}
