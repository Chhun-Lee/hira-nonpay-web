package dev.chhun.hospitalcompare.hospital;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jsKey 카카오맵 JavaScript 키. 페이지에 그대로 실리는 공개 키이고, 등록한 사이트 도메인에서만 동작한다.
 */
@ConfigurationProperties("kakao.map")
public record KakaoMapProperties(String jsKey) {

	public KakaoMapProperties {
		// 환경변수가 없으면 Spring은 오류 없이 "${KAKAO_MAP_KEY}" 문자열을 그대로 바인딩한다.
		if (jsKey == null || jsKey.isBlank() || jsKey.startsWith("${")) {
			throw new IllegalStateException("환경변수 KAKAO_MAP_KEY가 설정되지 않았습니다");
		}
	}

}
