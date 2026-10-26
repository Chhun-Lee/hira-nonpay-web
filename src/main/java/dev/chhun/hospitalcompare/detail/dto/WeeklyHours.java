package dev.chhun.hospitalcompare.detail.dto;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 요일별 진료 시간.
 *
 * @param days        시각을 읽은 요일
 * @param unknownDays 시각 칸은 있었지만 읽지 못한 요일. days와 겹치지 않는다
 *                    두 곳 어디에도 없는 요일은 휴진이다(다른 요일에 시각이 하나라도 있을 때).
 */
public record WeeklyHours(Map<DayOfWeek, OpenRange> days, Set<DayOfWeek> unknownDays) {

	public static final WeeklyHours NONE = new WeeklyHours(Map.of(), Set.of());

	public WeeklyHours {
		days = days.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(days));
		unknownDays = unknownDays.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(unknownDays));
		for (DayOfWeek day : unknownDays) {
			if (days.containsKey(day)) {
				throw new IllegalArgumentException("한 요일이 진료 시간과 모름에 함께 있습니다: " + day);
			}
		}
	}

	/** 진료 시각 칸이 한 요일이라도 있었는지(읽었든 못 읽었든). 없으면 진료시간 정보가 없는 기관이다. */
	public boolean hasAny() {
		return !days.isEmpty() || !unknownDays.isEmpty();
	}

}
