package dev.chhun.hospitalcompare.global.util;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * 초당 호출 수를 고른 간격(1/rate)으로 허가하는 토큰 버킷. 한꺼번에 몰아 쓰는 여유(버스트)는 두지 않는다.
 * 대기는 잠금 밖에서 해서 기다리는 가상 스레드끼리 서로를 막지 않는다.
 */
public class RateLimiter {

	private final long intervalNanos;
	private final LongSupplier nanoClock;
	private final Sleeper sleeper;
	private long nextFreeNanos;

	public RateLimiter(double permitsPerSecond, LongSupplier nanoClock, Sleeper sleeper) {
		if (!(permitsPerSecond > 0)) {
			throw new IllegalArgumentException("초당 허가 수는 0보다 커야 합니다: " + permitsPerSecond);
		}
		this.intervalNanos = Math.round(1_000_000_000d / permitsPerSecond);
		this.nanoClock = nanoClock;
		this.sleeper = sleeper;
		this.nextFreeNanos = nanoClock.getAsLong();
	}

	public static RateLimiter perSecond(double permitsPerSecond) {
		return new RateLimiter(permitsPerSecond, System::nanoTime, nanos -> Thread.sleep(Duration.ofNanos(nanos)));
	}

	/** 다음 허가 시각까지 기다린다. */
	public void acquire() throws InterruptedException {
		long waitNanos;
		synchronized (this) {
			long now = nanoClock.getAsLong();
			long slot = Math.max(now, nextFreeNanos);
			nextFreeNanos = slot + intervalNanos;
			waitNanos = slot - now;
		}
		if (waitNanos > 0) {
			sleeper.sleep(waitNanos);
		}
	}

	@FunctionalInterface
	public interface Sleeper {

		void sleep(long nanos) throws InterruptedException;

	}

}
