package dev.chhun.hospitalcompare.hira.exception;

/**
 * 공공데이터포털 게이트웨이가 요청을 막았다. 응답 루트가 {@code <OpenAPI_ServiceResponse>}다.
 * 사유코드(returnReasonCode) 예: 22 일 한도 초과, 23 초당 한도 초과, 30 미등록 키, 31 기한 만료.
 */
public class HiraGatewayException extends HiraException {

	private final String reasonCode;
	private final String authMessage;

	public HiraGatewayException(String request, String reasonCode, String authMessage) {
		super(request + " 게이트웨이 오류 " + reasonCode + " " + authMessage);
		this.reasonCode = reasonCode;
		this.authMessage = authMessage;
	}

	public String getReasonCode() {
		return reasonCode;
	}

	public String getAuthMessage() {
		return authMessage;
	}

	public boolean isDailyLimitExceeded() {
		return "22".equals(reasonCode);
	}

	public boolean isPerSecondLimitExceeded() {
		return "23".equals(reasonCode);
	}

}
