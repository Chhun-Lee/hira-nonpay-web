package dev.chhun.hospitalcompare.hira.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 심평원 API 공통 설정과 서비스별 설정. 한도·응답 시간이 서비스마다 달라 읽기 제한과 관문은 서비스별로 둔다.
 *
 * @param baseUrl        공공데이터포털 심평원 API 루트(https://apis.data.go.kr/B551182)
 * @param serviceKey     일반 인증키(Decoding). 로그·예외에 남기지 않는다.
 * @param connectTimeout 연결 제한(모든 서비스 공통)
 * @param retry          재시도할 수 있는 오류의 재시도 정책(모든 서비스 공통)
 * @param hospInfo       병원정보서비스
 * @param nonpay         비급여진료비정보서비스
 */
@ConfigurationProperties("hira")
public record HiraProperties(
		String baseUrl,
		String serviceKey,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue Retry retry,
		@DefaultValue HospInfo hospInfo,
		@DefaultValue Nonpay nonpay) {

	@ConstructorBinding
	public HiraProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException("hira.base-url이 비어 있습니다");
		}
		// 환경변수가 없으면 Spring은 오류 없이 "${HIRA_SERVICE_KEY}" 문자열을 그대로 바인딩한다.
		if (serviceKey == null || serviceKey.isBlank() || serviceKey.startsWith("${")) {
			throw new IllegalStateException("환경변수 HIRA_SERVICE_KEY가 설정되지 않았습니다");
		}
	}

	/** 타임아웃·관문·재시도는 기본값으로 만든다. */
	public HiraProperties(String baseUrl, String serviceKey) {
		this(baseUrl, serviceKey, Duration.ofSeconds(3), Retry.DEFAULT, HospInfo.DEFAULT, Nonpay.DEFAULT);
	}

	@Override
	public String toString() {
		return "HiraProperties[baseUrl=" + baseUrl + ", serviceKey=****, connectTimeout=" + connectTimeout
				+ ", retry=" + retry + ", hospInfo=" + hospInfo + ", nonpay=" + nonpay + "]";
	}

	private static void validate(String prefix, int maxConcurrency, double requestsPerSecond) {
		if (maxConcurrency < 1) {
			throw new IllegalStateException(prefix + ".max-concurrency는 1 이상이어야 합니다");
		}
		if (!(requestsPerSecond > 0)) {
			throw new IllegalStateException(prefix + ".requests-per-second는 0보다 커야 합니다");
		}
	}

	/** 서비스 하나의 읽기 제한과 호출 관문(동시 실행·초당 호출) 설정 */
	public interface ServiceSettings {

		Duration readTimeout();

		int maxConcurrency();

		double requestsPerSecond();

	}

	/**
	 * 병원정보서비스. 전국 1000건 페이지는 호출당 평균 6~8초가 걸렸다(2026-10-03 실측).
	 * 초당 한도 값은 공개되지 않아 동시 4·초당 5로 둔다(동시 8·초당 10에서도 한도 오류는 없었다).
	 */
	public record HospInfo(
			@DefaultValue("30s") Duration readTimeout,
			@DefaultValue("4") int maxConcurrency,
			@DefaultValue("5") double requestsPerSecond) implements ServiceSettings {

		public static final HospInfo DEFAULT = new HospInfo(Duration.ofSeconds(30), 4, 5);

		public HospInfo {
			validate("hira.hosp-info", maxConcurrency, requestsPerSecond);
		}

	}

	/** 비급여진료비정보서비스. 10건짜리 응답도 18초, 1000건은 59초가 걸렸다(2026-10-03 새벽 탐색). */
	public record Nonpay(
			@DefaultValue("120s") Duration readTimeout,
			@DefaultValue("4") int maxConcurrency,
			@DefaultValue("5") double requestsPerSecond) implements ServiceSettings {

		public static final Nonpay DEFAULT = new Nonpay(Duration.ofSeconds(120), 4, 5);

		public Nonpay {
			validate("hira.nonpay", maxConcurrency, requestsPerSecond);
		}

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
