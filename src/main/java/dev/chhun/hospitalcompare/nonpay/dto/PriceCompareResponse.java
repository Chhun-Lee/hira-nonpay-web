package dev.chhun.hospitalcompare.nonpay.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 항목별 가격 비교 결과.
 *
 * @param item      고른 항목. 비급여 ACTIVE가 없으면 null
 * @param total     자르기 전 반경 안 병원 수
 * @param hospitals 정렬해 최대 500곳
 * @param local     반경 기준값(병원별 최저가 기준). 3곳 미만이면 null
 * @param reference 심평원 기준값. 통계에 이 항목이 없거나 비급여 ACTIVE가 없으면 null
 */
public record PriceCompareResponse(
		BaseDates baseDate,
		NonpayItemResponse item,
		int total,
		List<PricedHospital> hospitals,
		LocalPriceStats local,
		ReferencePrices reference) {

	/**
	 * @param hospitals 병원 목록 ACTIVE 기준일
	 * @param nonpay    비급여 ACTIVE 기준일
	 */
	public record BaseDates(LocalDate hospitals, LocalDate nonpay) {
	}

}
