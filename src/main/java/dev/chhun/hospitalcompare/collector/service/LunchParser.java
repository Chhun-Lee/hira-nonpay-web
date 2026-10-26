package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.detail.dto.Lunch;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 점심시간 원문을 시작~끝 범위 하나로 읽는다. 공백을 모두 지운 뒤 "13시30분", "13시", "13:30", "13시:30분" 형식의 시각 두 개를
 * ~ 또는 - 로 이은 값만 받는다. 앞뒤에 다른 글자가 붙거나 범위가 둘 이상이면 읽지 않는다(UNKNOWN).
 * 1~6시는 오후로 본다(12시30분~1시30분 → 12:30~13:30).
 */
final class LunchParser {

	/** 시 + (시 뒤 분 | 콜론 뒤 분). 그룹 3개: 시, "시" 뒤 분, 콜론 뒤 분 */
	private static final String TIME = "(\\d{1,2})(?:시:?(\\d{1,2})?분?|:(\\d{1,2}))";
	private static final Pattern RANGE = Pattern.compile("^" + TIME + "[~\\-]" + TIME + "$");
	private static final Set<String> NONE = Set.of("없음", "휴진");
	/** 점심이 이보다 길면 잘못 읽은 것으로 본다(분) */
	private static final int MAX_LENGTH = 180;

	private LunchParser() {
	}

	static Lunch parse(String text) {
		if (text == null || text.isBlank()) {
			return Lunch.none(null);
		}
		String compact = text.replaceAll("\\s+", "").replace('～', '~').replace('〜', '~');
		if (NONE.contains(compact)) {
			return Lunch.none(text);
		}
		Matcher matcher = RANGE.matcher(compact);
		if (!matcher.matches()) {
			return Lunch.unknown(text);
		}
		Integer start = minutes(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
		Integer end = minutes(matcher.group(4), matcher.group(5) != null ? matcher.group(5) : matcher.group(6));
		if (start == null || end == null || start >= end || end - start > MAX_LENGTH) {
			return Lunch.unknown(text);
		}
		return Lunch.range(start, end, text);
	}

	private static Integer minutes(String hourText, String minuteText) {
		int hour = Integer.parseInt(hourText);
		int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
		if (hour >= 1 && hour <= 6) {
			hour += 12;
		}
		if (hour > 23 || minute > 59) {
			return null;
		}
		return hour * 60 + minute;
	}

}
