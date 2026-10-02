package dev.chhun.hospitalcompare.hira.dto;

/**
 * 공공데이터포털 게이트웨이 오류 응답. 루트가 {@code <OpenAPI_ServiceResponse>}다.
 */
public record GatewayErrorResponse(CmmMsgHeader cmmMsgHeader) {

	public record CmmMsgHeader(String errMsg, String returnAuthMsg, String returnReasonCode) {
	}

}
