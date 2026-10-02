package dev.chhun.hospitalcompare.hira;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 병원정보서비스 목록(getHospBasisList)의 item 하나. 필드명은 응답 그대로 두고, 쓰지 않는 인원수 필드는 받지 않는다.
 *
 * @param estbDd 개설일자(yyyyMMdd)
 * @param xPos   경도. 누락되면 null
 * @param yPos   위도. 누락되면 null
 */
public record HospBasisItem(
		String ykiho,
		String yadmNm,
		String clCd,
		String clCdNm,
		String sidoCd,
		String sidoCdNm,
		String sgguCd,
		String sgguCdNm,
		String emdongNm,
		String addr,
		String telno,
		String estbDd,
		@JsonProperty("XPos") Double xPos,
		@JsonProperty("YPos") Double yPos,
		Integer drTotCnt) {
}
