package dev.chhun.hospitalcompare;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 지금 진료 중 판정을 고정한다. 2026-10-07은 공휴일이 아닌 수요일이다. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

	public static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"));

	@Bean
	@Primary
	Clock fixedClock() {
		return Clock.fixed(NOW.toInstant(), NOW.getZone());
	}

}
