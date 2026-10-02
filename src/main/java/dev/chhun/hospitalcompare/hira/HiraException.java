package dev.chhun.hospitalcompare.hira;

/**
 * 심평원 API 호출 실패. 연결 실패, 본문으로 분류할 수 없는 HTTP 오류, 해석할 수 없는 응답이 여기에 해당한다.
 * 게이트웨이 오류와 API 오류는 하위 타입으로 구분한다.
 * <p>
 * 메시지에는 요청 URL을 넣지 않는다(서비스 키가 쿼리에 들어 있다).
 */
public class HiraException extends RuntimeException {

	public HiraException(String message) {
		super(message);
	}

}
