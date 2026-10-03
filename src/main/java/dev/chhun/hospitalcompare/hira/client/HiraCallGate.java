package dev.chhun.hospitalcompare.hira.client;

import dev.chhun.hospitalcompare.global.util.RateLimiter;
import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.exception.HiraException;
import dev.chhun.hospitalcompare.hira.exception.HiraGatewayException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

/**
 * 심평원 서비스 하나로 가는 모든 호출의 관문. 동시 실행 수와 초당 호출 수를 제한하고,
 * 재시도할 수 있는 오류(연결 실패, 타임아웃, 5xx, 초당 한도)는 대기 후 다시 보낸다. 재시도도 관문을 다시 통과한다.
 * 한도가 서비스 단위라 서비스마다 하나씩 둔다.
 */
public class HiraCallGate {

	private static final Logger log = LoggerFactory.getLogger(HiraCallGate.class);

	private final Semaphore concurrency;
	private final RateLimiter rateLimiter;
	private final RetryTemplate retryTemplate;
	private final LongAdder calls = new LongAdder();
	private final LongAdder retries = new LongAdder();
	private final LongAdder perSecondLimited = new LongAdder();
	private final LongAdder callNanos = new LongAdder();
	private final int maxAttempts;

	public HiraCallGate(int maxConcurrency, RateLimiter rateLimiter, HiraProperties.Retry retry) {
		this.concurrency = new Semaphore(maxConcurrency);
		this.rateLimiter = rateLimiter;
		this.maxAttempts = retry.maxRetries() + 1;
		// 지터: 동시에 실패한 요청들이 같은 시각에 다시 몰리지 않게 대기 시간을 흩뜨린다.
		this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
				.maxRetries(retry.maxRetries())
				.delay(retry.initialDelay())
				.multiplier(2)
				.maxDelay(retry.maxDelay())
				.jitter(retry.initialDelay().dividedBy(2))
				.predicate(e -> e instanceof HiraException hira && hira.isRetryable())
				.build());
		// 재시도가 로그에 남지 않으면 읽기 제한(30초) × 시도 수 동안 아무 흔적 없이 멈춘 것처럼 보인다.
		this.retryTemplate.setRetryListener(new RetryListener() {
			@Override
			public void onRetryableExecution(RetryPolicy policy, Retryable<?> retryable, RetryState state) {
				logIfRetrying(policy, state);
			}
		});
	}

	/** 실패한 시도 중 다시 보낼 것만 남긴다. 재시도할 수 없는 실패와 마지막 실패는 호출한 쪽이 예외로 받는다. */
	private void logIfRetrying(RetryPolicy policy, RetryState state) {
		if (state.isSuccessful()) {
			return;
		}
		int attempt = state.getRetryCount() + 1;
		Throwable failure = state.getLastException();
		if (attempt < maxAttempts && policy.shouldRetry(failure)) {
			log.warn("심평원 호출 실패로 재시도합니다({}/{}회째 실패): {}", attempt, maxAttempts, failure.getMessage());
		}
	}

	public static HiraCallGate create(HiraProperties.ServiceSettings service, HiraProperties.Retry retry) {
		return new HiraCallGate(service.maxConcurrency(), RateLimiter.perSecond(service.requestsPerSecond()), retry);
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
			// 실제로 요청을 보내고 받는 구간만 잰다. 위의 동시 실행·초당 제한 대기와 재시도 백오프는 뺀다.
			long started = System.nanoTime();
			try {
				return call.get();
			} finally {
				callNanos.add(System.nanoTime() - started);
			}
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
		return new Stats(calls.sum(), retries.sum(), perSecondLimited.sum(), callNanos.sum());
	}

	/**
	 * @param calls            실제로 보낸 요청 수(재시도 포함)
	 * @param retries          재시도 수
	 * @param perSecondLimited 초당 한도(23)로 거절된 수
	 * @param callNanos        실제 HTTP 호출에 걸린 시간의 합(ns). 관문 대기·재시도 백오프는 뺀다.
	 */
	public record Stats(long calls, long retries, long perSecondLimited, long callNanos) {

		public Stats minus(Stats before) {
			return new Stats(calls - before.calls, retries - before.retries, perSecondLimited - before.perSecondLimited,
					callNanos - before.callNanos);
		}

	}

}
