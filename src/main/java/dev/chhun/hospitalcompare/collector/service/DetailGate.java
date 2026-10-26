package dev.chhun.hospitalcompare.collector.service;

import java.util.Optional;

/**
 * 상세 STAGE를 화면용으로 바꿔도 되는지 판단한다. 대상이 없거나, 진료과목이 대량으로 비어 왔거나(응답 이상),
 * 세부정보가 있는 기관이 크게 줄었거나(부분 수집), 진료 시각을 많이 읽지 못했으면(형식 변경) 전환하지 않는다.
 * 점심·휴진 안내를 읽지 못한 것은 막지 않는다(품질 이슈로만 남는다).
 */
public class DetailGate {

	private final double maxEmptySubjectRatio;
	private final double minCountRatio;
	private final double maxInvalidHoursRatio;

	public DetailGate(double maxEmptySubjectRatio, double minCountRatio, double maxInvalidHoursRatio) {
		this.maxEmptySubjectRatio = maxEmptySubjectRatio;
		this.minCountRatio = minCountRatio;
		this.maxInvalidHoursRatio = maxInvalidHoursRatio;
	}

	/**
	 * @param previousActive  이전 상세 ACTIVE의 세부정보 기관 수. 첫 수집·시험 실행이면 null
	 * @param withoutSubjects 대상 중 진료과목이 하나도 없는 기관 수
	 * @param detailRows      세부정보가 있는 기관 수
	 * @return 통과하면 비어 있고, 실패하면 사유
	 */
	public Optional<String> check(Integer previousActive, int targets, int withoutSubjects, int detailRows,
			int hourFields, int invalidHourFields) {
		if (targets == 0) {
			return Optional.of("대상 기관이 없습니다");
		}
		if ((double) withoutSubjects / targets > maxEmptySubjectRatio) {
			return Optional.of("진료과목이 없는 기관 %d곳이 대상 %d곳의 %.1f%%로 허용치 %.1f%%를 넘었습니다(응답 이상 의심)"
					.formatted(withoutSubjects, targets, 100.0 * withoutSubjects / targets, 100 * maxEmptySubjectRatio));
		}
		if (previousActive != null && detailRows < previousActive * minCountRatio) {
			return Optional.of("세부정보가 있는 기관 %d곳이 이전 %d곳의 %.0f%%에 못 미칩니다(부분 수집 의심)"
					.formatted(detailRows, previousActive, 100 * minCountRatio));
		}
		if (hourFields > 0 && (double) invalidHourFields / hourFields > maxInvalidHoursRatio) {
			return Optional.of("읽지 못한 진료 시각 %d칸이 받은 %d칸의 %.1f%%로 허용치 %.1f%%를 넘었습니다(형식 변경 의심)"
					.formatted(invalidHourFields, hourFields, 100.0 * invalidHourFields / hourFields,
							100 * maxInvalidHoursRatio));
		}
		return Optional.empty();
	}

}
