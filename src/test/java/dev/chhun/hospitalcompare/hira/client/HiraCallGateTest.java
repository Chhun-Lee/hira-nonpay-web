package dev.chhun.hospitalcompare.hira.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.chhun.hospitalcompare.global.util.RateLimiter;
import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.exception.HiraException;
import dev.chhun.hospitalcompare.hira.exception.HiraGatewayException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HiraCallGateTest {

	private static final HiraProperties.Retry FAST_RETRY =
			new HiraProperties.Retry(3, Duration.ofMillis(5), Duration.ofMillis(20));

	private final HiraCallGate gate = new HiraCallGate(2, RateLimiter.perSecond(1000), FAST_RETRY);

	@Test
	void 재시도할_수_있는_오류는_다시_보내_성공한다() {
		AtomicInteger attempts = new AtomicInteger();

		String result = gate.call(() -> {
			if (attempts.incrementAndGet() < 3) {
				throw new HiraException("일시 오류", true);
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(attempts).hasValue(3);
		assertThat(gate.stats()).isEqualTo(new HiraCallGate.Stats(3, 2, 0));
	}

	@Test
	void 재시도할_수_없는_오류는_한_번만_시도하고_그대로_던진다() {
		HiraException fatal = new HiraException("요청 오류", false);
		AtomicInteger attempts = new AtomicInteger();

		assertThatThrownBy(() -> gate.call(() -> {
			attempts.incrementAndGet();
			throw fatal;
		})).isSameAs(fatal);
		assertThat(attempts).hasValue(1);
	}

	@Test
	void 재시도를_다_쓰면_마지막_오류를_던진다() {
		AtomicInteger attempts = new AtomicInteger();

		assertThatThrownBy(() -> gate.call(() -> {
			throw new HiraException("일시 오류 " + attempts.incrementAndGet(), true);
		})).isInstanceOf(HiraException.class).hasMessage("일시 오류 4");
		assertThat(gate.stats().retries()).isEqualTo(3);
	}

	@Test
	void 초당_한도_오류를_센다() {
		AtomicInteger attempts = new AtomicInteger();

		gate.call(() -> {
			if (attempts.incrementAndGet() == 1) {
				throw new HiraGatewayException("요청", "23", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR", "초당");
			}
			return "ok";
		});

		assertThat(gate.stats().perSecondLimited()).isEqualTo(1);
	}

	@Test
	void 동시에_보내는_요청은_최대_동시_실행_수를_넘지_않는다() throws Exception {
		AtomicInteger inFlight = new AtomicInteger();
		AtomicInteger maxInFlight = new AtomicInteger();
		List<Future<String>> futures = new ArrayList<>();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < 10; i++) {
				futures.add(executor.submit(() -> gate.call(() -> {
					maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
					sleep(50);
					inFlight.decrementAndGet();
					return "ok";
				})));
			}
			for (Future<String> future : futures) {
				future.get();
			}
		}

		assertThat(maxInFlight.get()).isEqualTo(2);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
