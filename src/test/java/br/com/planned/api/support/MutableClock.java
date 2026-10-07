package br.com.planned.api.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A fixed clock that a test can move, e.g. to check that an update refreshes {@code updatedAt}.
 * Register it with {@code @TestBean} in place of the app's {@code Clock} bean.
 */
public final class MutableClock extends Clock {

	private volatile Instant instant;
	private final ZoneId zone;

	public MutableClock(Instant instant, ZoneId zone) {
		this.instant = instant;
		this.zone = zone;
	}

	public void set(Instant instant) {
		this.instant = instant;
	}

	public void advance(Duration duration) {
		this.instant = instant.plus(duration);
	}

	@Override
	public Instant instant() {
		return instant;
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		// Shares nothing with this clock after the call; fine for "today" calculations.
		return new MutableClock(instant, zone);
	}
}
