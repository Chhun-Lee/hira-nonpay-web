package dev.chhun.hospitalcompare.hira.client;

import dev.chhun.hospitalcompare.global.util.RateLimiter;
import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.exception.HiraException;
import dev.chhun.hospitalcompare.hira.exception.HiraGatewayException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/**
 * 심평원 서비스 하나로 가는 모든 호출의 관문. 동시 실행 수와 초당 호출 수를 제한하고,
 * 재시도할 수 있는 오류(연결 실패, 타임아웃, 5xx, 초당 한도)는 대기 후 다시 보낸다. 재시도도 관문을 다시 통과한다.
 * 한도가 서비스 단위라 서비스마다 하나씩 둔다.
 */
public class HiraCallGate {

	private final Semaphore concurrency;
	private final RateLimiter rateLimiter;
	private final RetryTemplate retryTemplate;
	private final LongAdder calls = new LongAdder();
	private final LongAdder retries = new LongAdder();
	private final LongAdder perSecondLimited = new LongAdder();

	public HiraCallGate(int maxConcurrency, RateLimiter rateLimiter, HiraProperties.Retry retry) {
		this.concurrency = new Semaphore(maxConcurrency);
		this.rateLimiter = rateLimiter;
		// 지터: 동시에 실패한 요청들이 같은 시각에 다시 몰리지 않게 대기 시간을 흩뜨린다.
		this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
				.maxRetries(retry.maxRetries())
				.delay(retry.initialDelay())
				.multiplier(2)
				.maxDelay(retry.maxDelay())
				.jitter(retry.initialDelay().dividedBy(2))
				.predicate(e -> e instanceof HiraException hira && hira.isRetryable())
				.build());
	}

	public static HiraCallGate create(HiraProperties properties) {
		return new HiraCallGate(properties.maxConcurrency(), RateLimiter.perSecond(properties.requestsPerSecond()),
				properties.retry());
	}

	public <T> T call(Supplier<T> call) {
		AtomicInteger attempts = new AtomicInteger();
		try {
			return retryTemplate.execute(() -> {
				attempts.incrementAndGet();
				return attempt(call);
			});
		} catch (RetryException e) {
			Throwable last = e.getLastException();
			if (last instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			if (last instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new HiraException("심평원 호출이 중단되었습니다: " + last);
		} finally {
			retries.add(Math.max(0, attempts.get() - 1));
		}
	}

	private <T> T attempt(Supplier<T> call) throws InterruptedException {
		concurrency.acquire();
		try {
			rateLimiter.acquire();
			calls.increment();
			return call.get();
		} catch (HiraGatewayException e) {
			if (e.isPerSecondLimitExceeded()) {
				perSecondLimited.increment();
			}
			throw e;
		} finally {
			concurrency.release();
		}
	}

	/** 지금까지의 누적 지표 */
	public Stats stats() {
		return new Stats(calls.sum(), retries.sum(), perSecondLimited.sum());
	}

	/**
	 * @param calls            실제로 보낸 요청 수(재시도 포함)
	 * @param retries          재시도 수
	 * @param perSecondLimited 초당 한도(23)로 거절된 수
	 */
	public record Stats(long calls, long retries, long perSecondLimited) {

		public Stats minus(Stats before) {
			return new Stats(calls - before.calls, retries - before.retries, perSecondLimited - before.perSecondLimited);
		}

	}

}
