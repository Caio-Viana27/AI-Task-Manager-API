package br.com.planned.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A seeded lookup row of {@code TASK_STATUS}. Look it up by name, never by id. */
@Entity
@Table(name = "task_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskStatus {

	public static final String TODO = "TODO";
	public static final String IN_PROGRESS = "IN_PROGRESS";
	/** Set only by the system (PLAN §2, Status rules). */
	public static final String OVERDUE = "OVERDUE";
	public static final String DONE = "DONE";

	@Id
	private Integer id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;
}
