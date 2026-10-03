package dev.chhun.hospitalcompare.hira.dto;

/**
 * 항목별 병원 목록 요약(getNonPaymentItemHospList2)의 item 하나. 병원마다 한 줄이다.
 * 가격과 날짜는 검증 전이라 문자열로 받는다.
 *
 * @param minPrc 이 병원이 공개한 이 항목 가격의 최솟값(원)
 * @param maxPrc 최댓값(원). 등급·부위별로 여러 줄을 공개한 병원은 최소와 다르다
 */
public record NonpayHospPrice(
		String ykiho,
		String yadmNm,
		String clCd,
		String sidoCd,
		String sgguCd,
		String npayCd,
		String minPrc,
		String maxPrc,
		String adtFrDd) {
}
