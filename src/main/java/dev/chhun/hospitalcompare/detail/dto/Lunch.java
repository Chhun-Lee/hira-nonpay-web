package dev.chhun.hospitalcompare.detail.dto;

import dev.chhun.hospitalcompare.detail.entity.LunchKind;

/**
 * 점심시간. kind가 RANGE일 때만 start·end(자정부터 센 분)가 있다.
 *
 * @param text 원문. 없으면 null
 */
public record Lunch(LunchKind kind, Integer start, Integer end, String text) {

	public Lunch {
		if (kind == null) {
			throw new IllegalArgumentException("점심 종류가 없습니다");
		}
		if ((kind == LunchKind.RANGE) != (start != null && end != null)) {
			throw new IllegalArgumentException("점심 범위는 RANGE일 때만 있습니다: " + kind);
		}
	}

	public static Lunch none(String text) {
		return new Lunch(LunchKind.NONE, null, null, text);
	}

	public static Lunch unknown(String text) {
		return new Lunch(LunchKind.UNKNOWN, null, null, text);
	}

	public static Lunch range(int start, int end, String text) {
		return new Lunch(LunchKind.RANGE, start, end, text);
	}

	/** 범위가 있고 시작 ≤ minute < 끝 */
	public boolean contains(int minute) {
		return kind == LunchKind.RANGE && start <= minute && minute < end;
	}

}
