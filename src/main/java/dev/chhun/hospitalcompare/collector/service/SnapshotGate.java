package dev.chhun.hospitalcompare.collector.service;

import java.util.Optional;

/**
 * STAGE를 화면용으로 바꿔도 되는지 판단한다. 이전보다 건수가 크게 줄었거나(부분 수집),
 * 필수값 누락이 많으면(응답 형식 변경) 전환하지 않는다.
 */
public class SnapshotGate {

	private final double minCountRatio;
	private final double maxMissingRequiredRatio;

	public SnapshotGate(double minCountRatio, double maxMissingRequiredRatio) {
		this.minCountRatio = minCountRatio;
		this.maxMissingRequiredRatio = maxMissingRequiredRatio;
	}

	/**
	 * @param previousActiveCount 이전 ACTIVE 건수. 첫 수집이면 null
	 * @return 통과하면 비어 있고, 실패하면 사유
	 */
	public Optional<String> check(Integer previousActiveCount, int loadedCount, int receivedCount,
			int missingRequiredCount) {
		if (loadedCount == 0) {
			return Optional.of("적재된 기관이 없습니다");
		}
		if (receivedCount > 0 && (double) missingRequiredCount / receivedCount > maxMissingRequiredRatio) {
			return Optional.of("필수값 누락 %d건이 받은 %d건의 %.1f%%로 허용치 %.1f%%를 넘었습니다".formatted(
					missingRequiredCount, receivedCount, 100.0 * missingRequiredCount / receivedCount,
					100 * maxMissingRequiredRatio));
		}
		if (previousActiveCount != null && loadedCount < previousActiveCount * minCountRatio) {
			return Optional.of("적재 %d건이 이전 %d건의 %.0f%%에 못 미칩니다".formatted(
					loadedCount, previousActiveCount, 100 * minCountRatio));
		}
		return Optional.empty();
	}

}
