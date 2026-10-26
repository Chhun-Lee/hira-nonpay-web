package dev.chhun.hospitalcompare.detail.entity;

/** 점심시간 원문을 읽은 결과 */
public enum LunchKind {

	/** 시작~끝 범위 하나로 읽었다 */
	RANGE,

	/** 원문이 없거나 "없음"·"휴진"이다 */
	NONE,

	/** 읽지 못했다(형식이 다르거나 범위가 둘 이상) */
	UNKNOWN

}
