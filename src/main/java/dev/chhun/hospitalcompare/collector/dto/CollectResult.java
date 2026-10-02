package dev.chhun.hospitalcompare.collector.dto;

import java.time.Duration;

/**
 * 시군구 하나를 수집한 결과.
 *
 * @param calls               목록 API 호출 수
 * @param received            응답으로 받은 기관 수
 * @param snapshotRecordCount 수집 후 스냅샷 전체 기관 수
 * @param elapsed             전체 소요 시간
 * @param writeElapsed        그중 DB upsert에 쓴 시간
 */
public record CollectResult(
		String sgguCd,
		int calls,
		int received,
		long snapshotId,
		long snapshotRecordCount,
		Duration elapsed,
		Duration writeElapsed) {
}
