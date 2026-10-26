package dev.chhun.hospitalcompare.detail.dto;

/** 지금 진료 상태 */
public enum OpenState {

	/** 진료 중 */
	OPEN,

	/** 진료 끝(진료 전, 점심시간, 휴진 포함) */
	CLOSED,

	/** 확인 필요(진료시간 정보가 없거나 읽지 못함) */
	UNKNOWN

}
