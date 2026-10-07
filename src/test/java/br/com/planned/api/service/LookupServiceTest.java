package br.com.planned.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.planned.api.entity.Complexity;
import br.com.planned.api.entity.Priority;
import br.com.planned.api.entity.TaskStatus;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.support.IntegrationTest;

class LookupServiceTest extends IntegrationTest {

	@Autowired
	private LookupService lookupService;

	@Test
	void lookupsAreInSeedOrder() {
		assertThat(lookupService.priorities()).extracting(Priority::getName).containsExactly("LOW", "MEDIUM", "HIGH");
		assertThat(lookupService.statuses()).extracting(TaskStatus::getName)
				.containsExactly("TODO", "IN_PROGRESS", "OVERDUE", "DONE");
		assertThat(lookupService.complexities()).extracting(Complexity::getName)
				.containsExactly("EASY", "MEDIUM", "HARD");
		assertThat(lookupService.priorities()).extracting(Priority::getId).isSorted();
	}

	@Test
	void resolvesKnownNames() {
		assertThat(lookupService.priority(Priority.HIGH).getName()).isEqualTo("HIGH");
		assertThat(lookupService.status(TaskStatus.OVERDUE).getName()).isEqualTo("OVERDUE");
		assertThat(lookupService.complexity(Complexity.EASY).getName()).isEqualTo("EASY");
	}

	@Test
	void unknownPriorityIsInvalidPriority() {
		assertRejected(lookupService::priority, ErrorCode.INVALID_PRIORITY);
	}

	@Test
	void unknownStatusIsInvalidStatus() {
		assertRejected(lookupService::status, ErrorCode.INVALID_STATUS);
	}

	@Test
	void unknownComplexityIsInvalidComplexity() {
		assertRejected(lookupService::complexity, ErrorCode.INVALID_COMPLEXITY);
	}

	/** Unknown, wrong-case, and null names are all rejected with the lookup's own code. */
	private static void assertRejected(Consumer<String> resolve, ErrorCode code) {
		for (String name : new String[] { "NOPE", "medium", "", null }) {
			assertThatThrownBy(() -> resolve.accept(name))
					.as("name %s", name)
					.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
		}
	}
}
