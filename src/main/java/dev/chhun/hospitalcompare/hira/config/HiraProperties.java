package dev.chhun.hospitalcompare.hira.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl    공공데이터포털 심평원 API 루트(https://apis.data.go.kr/B551182)
 * @param serviceKey 일반 인증키(Decoding). 로그·예외에 남기지 않는다.
 */
@ConfigurationProperties("hira")
public record HiraProperties(String baseUrl, String serviceKey) {

	public HiraProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException("hira.base-url이 비어 있습니다");
		}
		// 환경변수가 없으면 Spring은 오류 없이 "${HIRA_SERVICE_KEY}" 문자열을 그대로 바인딩한다.
		if (serviceKey == null || serviceKey.isBlank() || serviceKey.startsWith("${")) {
			throw new IllegalStateException("환경변수 HIRA_SERVICE_KEY가 설정되지 않았습니다");
		}
	}

	@Override
	public String toString() {
		return "HiraProperties[baseUrl=" + baseUrl + ", serviceKey=****]";
	}

}
