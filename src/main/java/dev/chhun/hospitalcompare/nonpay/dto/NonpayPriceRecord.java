package dev.chhun.hospitalcompare.nonpay.dto;

import java.time.LocalDate;

/** 검증을 통과한 병원 가격 한 건. 가격은 원 */
public record NonpayPriceRecord(
		String npayCd,
		String ykiho,
		long minPrice,
		long maxPrice,
		String clCd,
		String sidoCd,
		String sgguCd,
		LocalDate adtFrDd) {
}
