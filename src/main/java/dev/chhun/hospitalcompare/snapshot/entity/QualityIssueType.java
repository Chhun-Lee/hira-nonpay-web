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
	UNKNOWN_STAT_KEY

}
