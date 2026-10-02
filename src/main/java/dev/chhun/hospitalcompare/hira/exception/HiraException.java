package dev.chhun.hospitalcompare.hira.exception;

/**
 * 심평원 API 호출 실패. 연결 실패, 본문으로 분류할 수 없는 HTTP 오류, 해석할 수 없는 응답이 여기에 해당한다.
 * 게이트웨이 오류와 API 오류는 하위 타입으로 구분한다.
 * <p>
 * 메시지에는 요청 URL을 넣지 않는다(서비스 키가 쿼리에 들어 있다).
 */
public class HiraException extends RuntimeException {

	private final boolean retryable;

	public HiraException(String message) {
		this(message, false);
	}

	public HiraException(String message, boolean retryable) {
		super(message);
		this.retryable = retryable;
	}

	/** 잠시 뒤 같은 요청을 다시 보내면 성공할 수 있는 오류인지(연결 실패, 타임아웃, 5xx, 초당 한도) */
	public boolean isRetryable() {
		return retryable;
	}

}
