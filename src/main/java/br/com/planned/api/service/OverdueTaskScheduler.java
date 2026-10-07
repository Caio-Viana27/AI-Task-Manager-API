package br.com.planned.api.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.planned.api.config.AppProperties;
import br.com.planned.api.entity.TaskStatus;
import br.com.planned.api.repository.TaskRepository;

/**
 * The nightly overdue job (PLAN §2, Status rules): at 00:05 in {@code app.timezone}, every
 * {@code TODO} or {@code IN_PROGRESS} task due before today becomes {@code OVERDUE}, for every
 * user and at every depth. It catches tasks whose due date passes while nobody edits them; writes
 * apply the same rule on their own (wave 2, D4).
 */
@Service
public class OverdueTaskScheduler {

	private static final Logger log = LoggerFactory.getLogger(OverdueTaskScheduler.class);

	private final TaskRepository taskRepository;
	private final LookupService lookupService;
	private final AppProperties properties;
	private final Clock clock;

	public OverdueTaskScheduler(TaskRepository taskRepository, LookupService lookupService, AppProperties properties,
			Clock clock) {
		this.taskRepository = taskRepository;
		this.lookupService = lookupService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(cron = "0 5 0 * * *", zone = "${app.timezone}")
	void run() {
		markOverdueTasks();
	}

	/**
	 * Marks late tasks {@code OVERDUE} in one bulk update, refreshing their {@code updatedAt}.
	 *
	 * @return how many tasks changed
	 */
	@Transactional
	public int markOverdueTasks() {
		LocalDate today = LocalDate.now(clock.withZone(properties.timezone()));
		int changed = taskRepository.markOverdue(
				lookupService.status(TaskStatus.OVERDUE),
				List.of(lookupService.status(TaskStatus.TODO), lookupService.status(TaskStatus.IN_PROGRESS)),
				today,
				Instant.now(clock));
		log.info("Marked {} tasks as overdue", changed);
		return changed;
	}
}
