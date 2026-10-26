package dev.chhun.hospitalcompare.detail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.chhun.hospitalcompare.detail.config.HoursProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class HolidayCalendarTest {

	private static final Clock OCT_2026 = Clock.fixed(Instant.parse("2026-10-07T01:00:00Z"), ZoneId.of("Asia/Seoul"));

	@Test
	void 목록에_있는_날짜만_공휴일이다() {
		HolidayCalendar calendar = new HolidayCalendar(new HoursProperties(List.of("2026-10-09", " 2026-12-25 ")),
				OCT_2026);

		assertThat(calendar.isHoliday(LocalDate.of(2026, 10, 9))).isTrue();
		assertThat(calendar.isHoliday(LocalDate.of(2026, 12, 25))).isTrue();
		assertThat(calendar.isHoliday(LocalDate.of(2026, 10, 7))).isFalse();
	}

	@Test
	void 날짜가_아닌_값이_있으면_기동을_막는다() {
		assertThatThrownBy(() -> new HolidayCalendar(new HoursProperties(List.of("2026-13-01")), OCT_2026))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("2026-13-01");
	}

	@Test
	void 올해_날짜가_없으면_경고를_남긴다(CapturedOutput output) {
		new HolidayCalendar(new HoursProperties(List.of("2025-12-25")), OCT_2026);

		assertThat(output).contains("2026년 날짜가 없습니다");
	}

}
