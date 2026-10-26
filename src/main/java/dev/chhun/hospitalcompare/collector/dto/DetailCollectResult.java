package dev.chhun.hospitalcompare.collector.dto;

import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import java.time.Duration;
import java.util.Map;

/**
 * 상세정보 수집 한 번의 결과.
 *
 * @param status            ACTIVE(전환), TRIAL(시험 실행 완료), FAILED
 * @param reason            FAILED·TRIAL의 사유
 * @param scope             "전국" 또는 시험 실행한 시군구 코드
 * @param targets           대상 병원급 기관 수
 * @param apiElapsed        실제 HTTP 응답을 기다린 시간의 합. 관문 대기와 재시도 백오프는 뺀다
 * @param detailRows        세부정보가 있는 기관 수(적재 행)
 * @param emptyDetails      세부정보가 비어 온 기관 수
 * @param subjectRows       적재한 진료과목 행 수
 * @param withoutSubjects   진료과목이 하나도 없는 기관 수
 * @param hourFields        받은 진료 시각 칸 수(시작·종료 각각)
 * @param invalidHourFields 그중 읽지 못한 칸 수
 * @param coverage          종별 코드 → 대상 수와 세부정보가 있는 기관 수
 * @param deleted           정리로 지운 상세 행 수
 */
public record DetailCollectResult(
		long snapshotId,
		SnapshotStatus status,
		String reason,
		boolean trial,
		String scope,
		int targets,
		long calls,
		long retries,
		long perSecondLimited,
		Duration elapsed,
		Duration apiElapsed,
		Duration dbElapsed,
		int detailRows,
		int emptyDetails,
		int subjectRows,
		int withoutSubjects,
		int hourFields,
		int invalidHourFields,
		Map<String, Coverage> coverage,
		int deleted,
		Map<QualityIssueType, Integer> issues) {

	/** 전환했거나 시험 실행을 마쳤으면 정상 종료다. */
	public boolean succeeded() {
		return status == SnapshotStatus.ACTIVE || status == SnapshotStatus.TRIAL;
	}

	/** 종별 하나의 대상 수와 세부정보가 있는 기관 수 */
	public record Coverage(int targets, int withDetail) {
	}

}
