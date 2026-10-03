package dev.chhun.hospitalcompare.collector.dto;

import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import java.time.Duration;
import java.util.Map;

/**
 * 비급여 수집 한 번의 결과.
 *
 * @param status         ACTIVE(전환), TRIAL(시험 실행 완료), FAILED
 * @param reason         FAILED·TRIAL의 사유
 * @param targetItems    가격을 받으려 한 항목 수
 * @param apiElapsed     실제 HTTP 응답을 기다린 시간의 합. 관문 대기와 재시도 백오프는 뺀다
 * @param received       받은 가격 행 수
 * @param loaded         STAGE에 적재된 가격 행 수
 * @param excluded       검증에서 뺀 가격 행 수
 * @param emptyItems     대상 항목 중 가격 행이 하나도 없는 항목 수
 * @param unmatchedYkiho 병원 목록 ACTIVE에 없는 병원 수. 병원 목록이 없거나 집계에 실패하면 null
 * @param deleted        정리로 지운 비급여 행 수
 */
public record NonpayCollectResult(
		long snapshotId,
		SnapshotStatus status,
		String reason,
		boolean trial,
		int targetItems,
		long calls,
		long retries,
		long perSecondLimited,
		Duration elapsed,
		Duration apiElapsed,
		Duration dbElapsed,
		int itemCodes,
		int statRows,
		int received,
		int loaded,
		int excluded,
		int emptyItems,
		Long unmatchedYkiho,
		int deleted,
		Map<QualityIssueType, Integer> issues) {

	/** 전환했거나 시험 실행을 마쳤으면 정상 종료다. */
	public boolean succeeded() {
		return status == SnapshotStatus.ACTIVE || status == SnapshotStatus.TRIAL;
	}

}
