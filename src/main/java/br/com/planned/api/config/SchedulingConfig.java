package br.com.planned.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} jobs, such as the nightly overdue job (PLAN §2, Status rules). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
