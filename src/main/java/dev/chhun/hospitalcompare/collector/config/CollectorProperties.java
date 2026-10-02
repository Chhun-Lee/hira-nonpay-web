package dev.chhun.hospitalcompare.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param numOfRows    목록 API 한 페이지 요청 크기. API가 더 작게 주면 그 크기로 나머지를 요청한다.
 * @param gate         전환 게이트 기준
 * @param runOnStartup collector 프로파일로 뜨면 바로 수집할지(테스트에서 끈다)
 */
@ConfigurationProperties("collector")
public record CollectorProperties(
		@DefaultValue("100") int numOfRows,
		@DefaultValue Gate gate,
		@DefaultValue("true") boolean runOnStartup) {

	/**
	 * @param minCountRatio           이전 ACTIVE 건수 대비 최소 비율
	 * @param maxMissingRequiredRatio 받은 행 대비 필수값 누락 최대 비율
	 */
	public record Gate(
			@DefaultValue("0.8") double minCountRatio,
			@DefaultValue("0.05") double maxMissingRequiredRatio) {
	}

}
