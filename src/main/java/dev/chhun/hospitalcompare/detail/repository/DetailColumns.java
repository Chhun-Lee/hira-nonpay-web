package dev.chhun.hospitalcompare.detail.repository;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** hospital_detail의 데이터 칸 이름과 변환. 적재와 조회가 같은 목록을 쓴다(순서가 곧 INSERT 자리 순서). */
final class DetailColumns {

	/** ykiho·snapshot_id를 뺀 데이터 칸. 요일 칸은 DayOfWeek 순서(월~일)다 */
	static final List<String> DATA = List.of(
			"mon_open", "mon_close", "tue_open", "tue_close", "wed_open", "wed_close", "thu_open", "thu_close",
			"fri_open", "fri_close", "sat_open", "sat_close", "sun_open", "sun_close", "hours_unknown_days",
			"lunch_week_kind", "lunch_week_start", "lunch_week_end", "lunch_week_text",
			"lunch_sat_kind", "lunch_sat_start", "lunch_sat_end", "lunch_sat_text",
			"reception_week_text", "reception_sat_text",
			"sunday_note_kind", "sunday_note_text", "holiday_note_kind", "holiday_note_text",
			"emergency_day", "emergency_night", "parking_capacity", "parking_fee", "parking_note");

	private DetailColumns() {
	}

	/** 요일 칸 접두어: MONDAY → mon */
	static String prefix(DayOfWeek day) {
		return day.name().substring(0, 3).toLowerCase(Locale.ROOT);
	}

	/** 읽지 못한 요일 → "MONDAY,SATURDAY". 없으면 null */
	static String days(Set<DayOfWeek> days) {
		return days.isEmpty() ? null : days.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
	}

	static Set<DayOfWeek> parseDays(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		return Collections.unmodifiableSet(Arrays.stream(value.split(","))
				.map(DayOfWeek::valueOf)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class))));
	}

}
