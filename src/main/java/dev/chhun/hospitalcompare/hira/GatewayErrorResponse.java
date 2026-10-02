package dev.chhun.hospitalcompare.hira;

/**
 * 공공데이터포털 게이트웨이 오류 응답. 루트가 {@code <OpenAPI_ServiceResponse>}다.
 */
record GatewayErrorResponse(CmmMsgHeader cmmMsgHeader) {

	record CmmMsgHeader(String errMsg, String returnAuthMsg, String returnReasonCode) {
	}

}
