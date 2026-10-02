package dev.chhun.hospitalcompare.collector.dto;

import dev.chhun.hospitalcompare.hospital.entity.QualityIssueType;
import dev.chhun.hospitalcompare.hospital.entity.SnapshotStatus;
import java.time.Duration;
import java.util.Map;

/**
 * 목록 수집 한 번의 결과.
 *
 * @param scope            "전국" 또는 시군구코드
 * @param status           ACTIVE(전환) 또는 FAILED
 * @param calls            실제로 보낸 요청 수(재시도 포함)
 * @param retries          재시도 수
 * @param perSecondLimited 초당 한도(23)로 거절된 수
 * @param apiElapsed       페이지 호출에 걸린 시간의 합(관문 대기·재시도 포함, 병렬이면 전체 시간보다 클 수 있다)
 * @param dbElapsed        적재에 걸린 시간의 합
 * @param received         응답으로 받은 기관 수
 * @param loaded           STAGE 스냅샷에 적재된 기관 수
 * @param deleted          정리로 지운 이전 스냅샷의 병원 행 수
 */
public record CollectResult(
		String scope,
		long snapshotId,
		SnapshotStatus status,
		String failureReason,
		long calls,
		long retries,
		long perSecondLimited,
		Duration elapsed,
		Duration apiElapsed,
		Duration dbElapsed,
		int totalCount,
		int received,
		int loaded,
		int deleted,
		Map<QualityIssueType, Integer> issues) {

	public boolean succeeded() {
		return status == SnapshotStatus.ACTIVE;
	}

}
