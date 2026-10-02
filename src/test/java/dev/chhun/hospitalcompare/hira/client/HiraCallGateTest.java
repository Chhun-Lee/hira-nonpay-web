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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
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
		HiraCallGate.Stats stats = gate.stats();
		assertThat(stats.calls()).isEqualTo(3);
		assertThat(stats.retries()).isEqualTo(2);
		assertThat(stats.perSecondLimited()).isZero();
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

	@Test
	void 호출_시간은_재시도_백오프를_빼고_실제_호출_구간만_잰다() {
		// 백오프는 지터를 빼도 300ms 이상이라, 호출 2번(각 50ms)의 합과 뚜렷이 갈린다.
		HiraCallGate slowBackoff = new HiraCallGate(2, RateLimiter.perSecond(1000),
				new HiraProperties.Retry(3, Duration.ofMillis(600), Duration.ofSeconds(5)));
		AtomicInteger attempts = new AtomicInteger();

		slowBackoff.call(() -> {
			sleep(50);
			if (attempts.incrementAndGet() == 1) {
				throw new HiraException("일시 오류", true);
			}
			return "ok";
		});

		Duration callTime = Duration.ofNanos(slowBackoff.stats().callNanos());
		assertThat(callTime).isGreaterThanOrEqualTo(Duration.ofMillis(100)).isLessThan(Duration.ofMillis(250));
	}

	@Test
	void 호출_시간은_동시_실행_수_대기를_뺀다() throws Exception {
		HiraCallGate single = new HiraCallGate(1, RateLimiter.perSecond(1000), FAST_RETRY);
		List<Future<String>> futures = new ArrayList<>();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < 2; i++) {
				futures.add(executor.submit(() -> single.call(() -> {
					sleep(200);
					return "ok";
				})));
			}
			for (Future<String> future : futures) {
				future.get();
			}
		}

		// 두 번째 호출은 첫 번째가 끝나기를 200ms 기다린다. 그 대기가 들어가면 600ms에 가까워진다.
		Duration callTime = Duration.ofNanos(single.stats().callNanos());
		assertThat(callTime).isGreaterThanOrEqualTo(Duration.ofMillis(400)).isLessThan(Duration.ofMillis(500));
	}

	@Test
	void 재시도하는_실패는_시도_번호와_사유를_WARN_한_줄로_남긴다(CapturedOutput output) {
		AtomicInteger attempts = new AtomicInteger();

		gate.call(() -> {
			if (attempts.incrementAndGet() == 1) {
				throw new HiraException("getHospBasisList(sgguCd=전국, pageNo=3) 호출 실패: 읽기 시간 초과", true);
			}
			return "ok";
		});

		assertThat(retryLines(output)).singleElement().satisfies(line -> assertThat(line)
				.contains("WARN", "1/4", "getHospBasisList(sgguCd=전국, pageNo=3) 호출 실패: 읽기 시간 초과"));
	}

	@Test
	void 재시도를_다_쓰면_다시_보낼_실패마다_한_줄이고_마지막_실패는_남기지_않는다(CapturedOutput output) {
		AtomicInteger attempts = new AtomicInteger();

		assertThatThrownBy(() -> gate.call(() -> {
			throw new HiraException("일시 오류 " + attempts.incrementAndGet(), true);
		})).isInstanceOf(HiraException.class);

		assertThat(retryLines(output)).hasSize(3);
		assertThat(retryLines(output).get(0)).contains("1/4", "일시 오류 1");
		assertThat(retryLines(output).get(2)).contains("3/4", "일시 오류 3");
		assertThat(output.getOut()).doesNotContain("4/4");
	}

	@Test
	void 재시도할_수_없는_실패는_재시도_로그를_남기지_않는다(CapturedOutput output) {
		assertThatThrownBy(() -> gate.call(() -> {
			throw new HiraException("요청 오류", false);
		})).isInstanceOf(HiraException.class);

		assertThat(retryLines(output)).isEmpty();
	}

	private static List<String> retryLines(CapturedOutput output) {
		return output.getOut().lines().filter(line -> line.contains("재시도")).toList();
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
