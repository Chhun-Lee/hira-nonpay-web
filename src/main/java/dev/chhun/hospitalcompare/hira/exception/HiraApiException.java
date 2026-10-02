package dev.chhun.hospitalcompare.hira.exception;

/**
 * 심평원 API가 {@code <response>}로 답했지만 header.resultCode가 00이 아니다. HTTP 상태는 200으로 온다.
 */
public class HiraApiException extends HiraException {

	private final String resultCode;
	private final String resultMessage;

	public HiraApiException(String request, String resultCode, String resultMessage) {
		super(request + " API 오류 " + resultCode + " " + resultMessage);
		this.resultCode = resultCode;
		this.resultMessage = resultMessage;
	}

	public String getResultCode() {
		return resultCode;
	}

	public String getResultMessage() {
		return resultMessage;
	}

}
