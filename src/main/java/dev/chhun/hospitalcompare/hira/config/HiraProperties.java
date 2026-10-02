package dev.chhun.hospitalcompare.hira.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param baseUrl           공공데이터포털 심평원 API 루트(https://apis.data.go.kr/B551182)
 * @param serviceKey        일반 인증키(Decoding). 로그·예외에 남기지 않는다.
 * @param connectTimeout    연결 제한
 * @param readTimeout       응답 제한
 * @param maxConcurrency    서비스 하나에 동시에 보내는 최대 요청 수
 * @param requestsPerSecond 서비스 하나에 보내는 초당 최대 요청 수. 공개된 한도가 없어 실측으로 조정한다.
 * @param retry             재시도할 수 있는 오류의 재시도 정책
 */
@ConfigurationProperties("hira")
public record HiraProperties(
		String baseUrl,
		String serviceKey,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("10s") Duration readTimeout,
		@DefaultValue("4") int maxConcurrency,
		@DefaultValue("5") double requestsPerSecond,
		@DefaultValue Retry retry) {

	@ConstructorBinding
	public HiraProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException("hira.base-url이 비어 있습니다");
		}
		// 환경변수가 없으면 Spring은 오류 없이 "${HIRA_SERVICE_KEY}" 문자열을 그대로 바인딩한다.
		if (serviceKey == null || serviceKey.isBlank() || serviceKey.startsWith("${")) {
			throw new IllegalStateException("환경변수 HIRA_SERVICE_KEY가 설정되지 않았습니다");
		}
		if (maxConcurrency < 1) {
			throw new IllegalStateException("hira.max-concurrency는 1 이상이어야 합니다");
		}
		if (!(requestsPerSecond > 0)) {
			throw new IllegalStateException("hira.requests-per-second는 0보다 커야 합니다");
		}
	}

	/** 타임아웃·관문·재시도는 기본값으로 만든다. */
	public HiraProperties(String baseUrl, String serviceKey) {
		this(baseUrl, serviceKey, Duration.ofSeconds(3), Duration.ofSeconds(10), 4, 5, Retry.DEFAULT);
	}

	@Override
	public String toString() {
		return "HiraProperties[baseUrl=" + baseUrl + ", serviceKey=****, connectTimeout=" + connectTimeout
				+ ", readTimeout=" + readTimeout + ", maxConcurrency=" + maxConcurrency
				+ ", requestsPerSecond=" + requestsPerSecond + ", retry=" + retry + "]";
	}

	/**
	 * @param maxRetries   첫 시도 뒤 최대 재시도 횟수
	 * @param initialDelay 첫 재시도 전 대기. 이후 두 배씩 늘린다.
	 * @param maxDelay     대기 상한
	 */
	public record Retry(
			@DefaultValue("3") int maxRetries,
			@DefaultValue("500ms") Duration initialDelay,
			@DefaultValue("5s") Duration maxDelay) {

		public static final Retry DEFAULT = new Retry(3, Duration.ofMillis(500), Duration.ofSeconds(5));

	}

}
