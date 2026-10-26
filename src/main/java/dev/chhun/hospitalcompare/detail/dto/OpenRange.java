package dev.chhun.hospitalcompare.detail.dto;

/**
 * 하루 진료 시간. 자정부터 센 분이다. 하루 안에서 끝난다(자정을 넘는 진료는 해석기가 "모름"으로 둔다).
 *
 * @param open  시작(0 이상)
 * @param close 종료(시작보다 크고 1440 이하. 1440은 24:00)
 */
public record OpenRange(int open, int close) {

	/** 하루의 분 */
	public static final int DAY = 1440;

	public OpenRange {
		if (open < 0 || close <= open || close > DAY) {
			throw new IllegalArgumentException("진료 시간 범위가 아닙니다: " + open + "~" + close);
		}
	}

	/** 시작 ≤ minute < 종료 */
	public boolean contains(int minute) {
		return open <= minute && minute < close;
	}

}
