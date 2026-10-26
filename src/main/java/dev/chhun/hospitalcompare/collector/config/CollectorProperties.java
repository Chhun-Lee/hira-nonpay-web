package dev.chhun.hospitalcompare.collector.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param numOfRows    병원 목록 API 한 페이지 요청 크기. API가 더 작게 주면 그 크기로 나머지를 요청한다.
 * @param gate         병원 목록 전환 게이트 기준
 * @param runOnStartup collector 프로파일로 뜨면 바로 수집할지(테스트에서 끈다)
 * @param nonpay       비급여 수집
 * @param detail       상세정보 수집
 */
@ConfigurationProperties("collector")
public record CollectorProperties(
		@DefaultValue("1000") int numOfRows,
		@DefaultValue Gate gate,
		@DefaultValue("true") boolean runOnStartup,
		@DefaultValue Nonpay nonpay,
		@DefaultValue Detail detail) {

	/**
	 * @param minCountRatio           이전 ACTIVE 건수 대비 최소 비율
	 * @param maxMissingRequiredRatio 받은 행 대비 필수값 누락 최대 비율
	 */
	public record Gate(
			@DefaultValue("0.8") double minCountRatio,
			@DefaultValue("0.05") double maxMissingRequiredRatio) {
	}

	/**
	 * @param numOfRows        비급여 API 한 페이지 요청 크기. 1000건까지 받아 준다(2026-10-03 탐색)
	 * @param gate             비급여 전환 게이트 기준
	 * @param progressInterval 진행 로그 간격. 몇 시간짜리 실행이라 주기적으로 남긴다
	 */
	public record Nonpay(
			@DefaultValue("1000") int numOfRows,
			@DefaultValue NonpayGateSettings gate,
			@DefaultValue("5m") Duration progressInterval) {

		public Nonpay {
			if (numOfRows < 1) {
				throw new IllegalStateException("collector.nonpay.num-of-rows는 1 이상이어야 합니다");
			}
		}

	}

	/**
	 * @param minCountRatio     이전 비급여 ACTIVE의 가격 행 대비 최소 비율
	 * @param maxInvalidRatio   받은 가격 행 대비 제외 행 최대 비율
	 * @param maxEmptyItemRatio 대상 항목 대비 가격 행이 없는 항목 최대 비율
	 */
	public record NonpayGateSettings(
			@DefaultValue("0.8") double minCountRatio,
			@DefaultValue("0.05") double maxInvalidRatio,
			@DefaultValue("0.05") double maxEmptyItemRatio) {
	}

	/**
	 * @param numOfRows        상세정보 API 한 페이지 요청 크기. 기관 하나의 응답은 최대 35행이었다(2026-10-05 탐색)
	 * @param gate             상세 전환 게이트 기준
	 * @param progressInterval 진행 로그 간격
	 */
	public record Detail(
			@DefaultValue("100") int numOfRows,
			@DefaultValue DetailGateSettings gate,
			@DefaultValue("1m") Duration progressInterval) {

		public Detail {
			if (numOfRows < 1) {
				throw new IllegalStateException("collector.detail.num-of-rows는 1 이상이어야 합니다");
			}
		}

	}

	/**
	 * @param maxEmptySubjectRatio 대상 대비 진료과목이 없는 기관 최대 비율
	 * @param minCountRatio        이전 상세 ACTIVE의 세부정보 기관 수 대비 최소 비율
	 * @param maxInvalidHoursRatio 받은 진료 시각 칸 대비 읽지 못한 칸 최대 비율
	 */
	public record DetailGateSettings(
			@DefaultValue("0.05") double maxEmptySubjectRatio,
			@DefaultValue("0.8") double minCountRatio,
			@DefaultValue("0.05") double maxInvalidHoursRatio) {
	}

}
