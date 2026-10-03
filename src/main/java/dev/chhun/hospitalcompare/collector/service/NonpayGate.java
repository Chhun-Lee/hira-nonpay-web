package dev.chhun.hospitalcompare.collector.service;

import java.util.Optional;

/**
 * 비급여 STAGE를 화면용으로 바꿔도 되는지 판단한다. 비어 있거나, 응답 형식이 바뀐 듯하거나(제외 행 급증),
 * 일부만 받은 듯하거나(건수 급감), 항목별 호출이 대량으로 비어 왔으면 전환하지 않는다.
 */
public class NonpayGate {

	private final double minCountRatio;
	private final double maxInvalidRatio;
	private final double maxEmptyItemRatio;

	public NonpayGate(double minCountRatio, double maxInvalidRatio, double maxEmptyItemRatio) {
		this.minCountRatio = minCountRatio;
		this.maxInvalidRatio = maxInvalidRatio;
		this.maxEmptyItemRatio = maxEmptyItemRatio;
	}

	/**
	 * @param previousActive 이전 비급여 ACTIVE의 가격 행 수. 첫 수집이면 null
	 * @param emptyItems     대상 항목 중 적재된 가격 행이 하나도 없는 항목 수
	 * @return 통과하면 비어 있고, 실패하면 사유
	 */
	public Optional<String> check(Integer previousActive, int loaded, int received, int excluded, int targetItems,
			int emptyItems) {
		if (loaded == 0) {
			return Optional.of("적재된 가격이 없습니다");
		}
		if (received > 0 && (double) excluded / received > maxInvalidRatio) {
			return Optional.of("제외된 가격 행 %d건이 받은 %d건의 %.1f%%로 허용치 %.1f%%를 넘었습니다(응답 형식 변경 의심)"
					.formatted(excluded, received, 100.0 * excluded / received, 100 * maxInvalidRatio));
		}
		if (previousActive != null && loaded < previousActive * minCountRatio) {
			return Optional.of("적재 %d건이 이전 %d건의 %.0f%%에 못 미칩니다(부분 수집 의심)"
					.formatted(loaded, previousActive, 100 * minCountRatio));
		}
		if (targetItems > 0 && (double) emptyItems / targetItems > maxEmptyItemRatio) {
			return Optional.of("가격이 하나도 없는 항목 %d개가 대상 %d개의 %.1f%%로 허용치 %.1f%%를 넘었습니다"
					.formatted(emptyItems, targetItems, 100.0 * emptyItems / targetItems, 100 * maxEmptyItemRatio));
		}
		return Optional.empty();
	}

}
