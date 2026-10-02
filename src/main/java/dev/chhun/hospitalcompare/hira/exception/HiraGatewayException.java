package dev.chhun.hospitalcompare.hira.exception;

/**
 * 공공데이터포털 게이트웨이가 요청을 막았다. 응답 루트가 {@code <OpenAPI_ServiceResponse>}이고 HTTP 403으로 온다.
 * 사유코드(returnReasonCode) 예: 22 일 한도 초과, 23 초당 한도 초과, 30 미등록 키, 31 기한 만료.
 * 초당 한도(23)만 잠시 뒤 다시 보내면 풀린다.
 */
public class HiraGatewayException extends HiraException {

	private final String reasonCode;
	private final String errorName;
	private final String authMessage;

	public HiraGatewayException(String request, String reasonCode, String errorName, String authMessage) {
		super(request + " 게이트웨이 오류 " + reasonCode + " " + errorName + " " + authMessage, "23".equals(reasonCode));
		this.reasonCode = reasonCode;
		this.errorName = errorName;
		this.authMessage = authMessage;
	}

	public String getReasonCode() {
		return reasonCode;
	}

	/** errMsg. 예: LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR */
	public String getErrorName() {
		return errorName;
	}

	/** returnAuthMsg. 한글 설명 */
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
