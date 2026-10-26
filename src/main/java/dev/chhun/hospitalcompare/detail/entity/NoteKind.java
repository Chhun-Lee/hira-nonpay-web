package dev.chhun.hospitalcompare.detail.entity;

/** 일요일·공휴일 휴진 안내 원문을 나눈 결과. 원문이 없으면 분류도 없다(null). */
public enum NoteKind {

	/** 휴진한다 */
	CLOSED,

	/** 진료한다(정상진료, 24시간 진료) */
	OPEN,

	/** 어느 쪽인지 정할 수 없다(시간 메모, 당직 안내 등) */
	UNKNOWN

}
