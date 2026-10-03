package dev.chhun.hospitalcompare.nonpay.dto;

import java.time.LocalDate;

/** 검증을 통과한 항목 코드 한 건 */
public record NonpayItemRecord(
		String npayCd,
		String npayKorNm,
		String mdivCd,
		String mdivCdNm,
		String sdivCd,
		String sdivCdNm,
		String dtlDivCd,
		String dtlDivCdNm,
		LocalDate adtFrDd,
		LocalDate adtEndDd) {
}
