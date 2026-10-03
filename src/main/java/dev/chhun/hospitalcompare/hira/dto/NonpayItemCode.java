package dev.chhun.hospitalcompare.hira.dto;

/**
 * 항목코드 조회(getNonPaymentItemCodeList2)의 item 하나. 필드명은 응답 그대로다.
 *
 * @param adtFrDd  적용 시작일(yyyyMMdd)
 * @param adtEndDd 적용 종료일(yyyyMMdd, 무기한이면 99991231)
 */
public record NonpayItemCode(
		String npayCd,
		String npayKorNm,
		String npayMdivCd,
		String npayMdivCdNm,
		String npaySdivCd,
		String npaySdivCdNm,
		String npayDtlDivCd,
		String npayDtlDivCdNm,
		String adtFrDd,
		String adtEndDd) {
}
