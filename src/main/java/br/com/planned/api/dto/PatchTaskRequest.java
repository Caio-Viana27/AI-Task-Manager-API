package br.com.planned.api.dto;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import br.com.planned.api.dto.validation.NotNullWhenPresent;
import br.com.planned.api.dto.validation.PresenceTracking;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code PATCH /api/v1/tasks/{id}} (wave 2, D2): an absent field is left unchanged, and an explicit
 * {@code null} clears it. Only {@code dueDate}, {@code complexity}, and {@code estimatedHours} can be
 * cleared; {@code null}
 * for any other field is a validation error.
 *
 * <p>A class rather than a record of {@code Optional}s, because Jackson 3 maps both an absent
 * property and an explicit {@code null} to {@code Optional.empty()}. Jackson calls a setter only
 * for a property that is in the body, so each setter records that its field was sent.
 */
@Schema(description = "Any subset of the PUT fields. Absent = unchanged; null clears dueDate, complexity, or estimatedHours.")
@NotNullWhenPresent({ PatchTaskRequest.TITLE, PatchTaskRequest.DESCRIPTION, PatchTaskRequest.PRIORITY,
		PatchTaskRequest.STATUS })
public class PatchTaskRequest implements PresenceTracking {

	public static final String TITLE = "title";
	public static final String DESCRIPTION = "description";
	public static final String DUE_DATE = "dueDate";
	public static final String PRIORITY = "priority";
	public static final String STATUS = "status";
	public static final String COMPLEXITY = "complexity";
	public static final String ESTIMATED_HOURS = "estimatedHours";

	private final Set<String> present = new HashSet<>();

	@Schema(example = "Write the quarterly report", maxLength = 100)
	@Size(max = 100)
	@Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
	private String title;

	@Schema(example = "Collect the numbers and draft the summary", maxLength = 500)
	@Size(max = 500)
	@Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
	private String description;

	@Schema(example = "2026-10-31", description = "null clears it")
	private LocalDate dueDate;

	@Schema(example = "HIGH", description = "LOW, MEDIUM, or HIGH")
	private String priority;

	@Schema(example = "DONE", description = "TODO, IN_PROGRESS, or DONE. OVERDUE is set only by the system.")
	private String status;

	@Schema(example = "MEDIUM", description = "EASY, MEDIUM, HARD, or null to clear it")
	private String complexity;

	@Schema(example = "8", minimum = "1", maximum = "999", description = "Whole hours, 1 to 999, or null to clear it")
	@Min(1)
	@Max(999)
	private Integer estimatedHours;

	/** @return whether the body contained {@code field} (one of this class's field-name constants) */
	@Override
	public boolean isPresent(String field) {
		return present.contains(field);
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
		present.add(TITLE);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
		present.add(DESCRIPTION);
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
		present.add(DUE_DATE);
	}

	public String getPriority() {
		return priority;
	}

	public void setPriority(String priority) {
		this.priority = priority;
		present.add(PRIORITY);
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
		present.add(STATUS);
	}

	public String getComplexity() {
		return complexity;
	}

	public void setComplexity(String complexity) {
		this.complexity = complexity;
		present.add(COMPLEXITY);
	}

	public Integer getEstimatedHours() {
		return estimatedHours;
	}

	public void setEstimatedHours(Integer estimatedHours) {
		this.estimatedHours = estimatedHours;
		present.add(ESTIMATED_HOURS);
	}
}
