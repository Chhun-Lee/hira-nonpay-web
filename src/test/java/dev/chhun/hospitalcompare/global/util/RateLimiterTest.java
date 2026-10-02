package dev.chhun.hospitalcompare.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

	private static final long MILLI = 1_000_000L;

	private final AtomicLong now = new AtomicLong(0);
	private final List<Long> sleeps = new ArrayList<>();

	@Test
	void 초당_5회면_200ms_간격으로_허가한다() throws InterruptedException {
		RateLimiter limiter = new RateLimiter(5, now::get, sleeps::add);

		limiter.acquire();
		limiter.acquire();
		limiter.acquire();

		assertThat(sleeps).containsExactly(200 * MILLI, 400 * MILLI);
	}

	@Test
	void 쉬는_동안_쌓인_시간은_다음_호출에_넘겨주지_않는다() throws InterruptedException {
		RateLimiter limiter = new RateLimiter(5, now::get, sleeps::add);
		limiter.acquire();

		now.set(1_000 * MILLI);
		limiter.acquire();
		limiter.acquire();

		assertThat(sleeps).containsExactly(200 * MILLI);
	}

	@Test
	void 허가율은_0보다_커야_한다() {
		assertThatThrownBy(() -> new RateLimiter(0, now::get, sleeps::add))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
