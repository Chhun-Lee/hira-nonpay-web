package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import java.util.regex.Pattern;

/**
 * 세부정보의 요일별 진료 시작·종료(HHmm 4자리)를 분으로 읽는다. 시작이 0보다 큰데 종료가 0000이면 자정(24:00)이다.
 * 그래도 종료가 시작보다 이르거나 같으면 보정하지 않고 AMBIGUOUS로 둔다. 0900~0100(자정 넘김? 12시간제 13시?),
 * 0900~0630(12시간제 18시 30분), 0000~0000(24시간? 휴진 요일 자리채움?)을 값만으로 가릴 수 없어서다.
 * 24시간 진료는 0000~2400만 인정한다.
 */
final class DetailHoursParser {

	private static final Pattern HHMM = Pattern.compile("\\d{4}");

	private DetailHoursParser() {
	}

	enum Outcome {

		/** 시작·종료 칸이 모두 없다 */
		ABSENT,

		VALID,

		/** 한 칸만 있거나 형식·범위가 틀렸다(형식 변경 신호, 게이트가 센다) */
		INVALID,

		/** 형식은 맞지만 종료 ≤ 시작이라 뜻을 정할 수 없다(기관 입력 문제, 게이트는 세지 않는다) */
		AMBIGUOUS

	}

	/** 한 요일의 결과. VALID일 때만 range가 있다. */
	record DayResult(Outcome outcome, OpenRange range) {
	}

	static DayResult parse(String start, String end) {
		boolean noStart = start == null || start.isBlank();
		boolean noEnd = end == null || end.isBlank();
		if (noStart && noEnd) {
			return new DayResult(Outcome.ABSENT, null);
		}
		Integer open = noStart ? null : minutes(start);
		Integer close = noEnd ? null : minutes(end);
		if (open == null || close == null) {
			return new DayResult(Outcome.INVALID, null);
		}
		int adjustedClose = close == 0 && open > 0 ? OpenRange.DAY : close;
		if (adjustedClose <= open) {
			return new DayResult(Outcome.AMBIGUOUS, null);
		}
		return new DayResult(Outcome.VALID, new OpenRange(open, adjustedClose));
	}

	/** HHmm → 자정부터 센 분. 4자리 숫자가 아니거나 분이 59를 넘거나 2400을 넘으면 null */
	static Integer minutes(String hhmm) {
		String value = hhmm.strip();
		if (!HHMM.matcher(value).matches()) {
			return null;
		}
		int hour = Integer.parseInt(value.substring(0, 2));
		int minute = Integer.parseInt(value.substring(2));
		int total = hour * 60 + minute;
		if (minute > 59 || total > OpenRange.DAY) {
			return null;
		}
		return total;
	}

}
