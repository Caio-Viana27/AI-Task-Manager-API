package br.com.planned.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A seeded lookup row of {@code PRIORITIES}. Look it up by name, never by id. The id follows the
 * seed order, so it also orders priorities from lowest to highest (wave 2, D3).
 */
@Entity
@Table(name = "priorities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Priority {

	public static final String LOW = "LOW";
	public static final String MEDIUM = "MEDIUM";
	public static final String HIGH = "HIGH";

	@Id
	private Integer id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;
}
