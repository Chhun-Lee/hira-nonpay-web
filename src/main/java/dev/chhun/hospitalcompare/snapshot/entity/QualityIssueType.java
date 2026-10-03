package dev.chhun.hospitalcompare.snapshot.entity;

public enum QualityIssueType {

	/** ykiho·기관명·종별코드 중 하나라도 비어 있어 적재하지 않았다 */
	MISSING_REQUIRED,

	/** 좌표가 없다. 적재는 하지만 반경 검색에서 빠진다 */
	MISSING_COORDINATES,

	/** 좌표가 국내 범위 밖이라 좌표만 비우고 적재했다 */
	COORDINATES_OUT_OF_RANGE,

	/** 개설일이 yyyyMMdd 날짜가 아니라 개설일만 비우고 적재했다 */
	INVALID_DATE

}
