package dev.chhun.hospitalcompare.snapshot.entity;

public enum QualityIssueType {

	/** ykiho·기관명·종별코드 중 하나라도 비어 있어 적재하지 않았다 */
	MISSING_REQUIRED,

	/** 좌표가 없다. 적재는 하지만 반경 검색에서 빠진다 */
	MISSING_COORDINATES,

	/** 좌표가 국내 범위 밖이라 좌표만 비우고 적재했다 */
	COORDINATES_OUT_OF_RANGE,

	/** 개설일이 yyyyMMdd 날짜가 아니라 개설일만 비우고 적재했다 */
	INVALID_DATE,

	/** 가격이 숫자가 아니거나 0 이하이거나 최소가 최대보다 크다. 가격 행이면 적재하지 않고, 통계 칸이면 그 값만 비운다 */
	INVALID_PRICE,

	/** 값이 열 길이를 넘어 잘라서 적재했다 */
	VALUE_TRUNCATED,

	/** 통계 응답에 모르는 칸 접미사가 왔다. 그 칸은 버렸다(행정구역 개편 등) */
	UNKNOWN_STAT_KEY,

	/** 진료 시각이 HHmm이 아니거나 범위를 벗어나거나 시작·종료 중 하나만 있다. 그 요일을 "모름"으로 두고 적재했다 */
	HOURS_INVALID,

	/** 진료 시각 형식은 맞지만 종료가 시작보다 이르거나 같다(자정 넘김·12시간제·24시간·자리채움을 가릴 수 없음). 그 요일을 "모름"으로 두고 적재했다 */
	HOURS_AMBIGUOUS,

	/** 점심 문자열을 범위 하나로 읽지 못했다. 원문만 두고 종류를 UNKNOWN으로 적재했다 */
	LUNCH_UNPARSED,

	/** 휴진 안내가 휴진·진료 어느 쪽으로도 나뉘지 않았다. 원문만 두고 종류를 UNKNOWN으로 적재했다 */
	CLOSED_NOTE_UNCLASSIFIED,

	/** 전문의 수나 주차 대수가 0 이상의 정수가 아니다. 그 값만 비우고 적재했다 */
	INVALID_COUNT

}
