package dev.chhun.hospitalcompare.snapshot.entity;

public enum SnapshotStatus {

	/** 적재 중. 화면에 노출하지 않는다. */
	STAGE,

	/** 사용자에게 보여 주는 스냅샷. 한 번에 하나만 둔다. */
	ACTIVE,

	/** 새 스냅샷으로 교체된 이전 버전 */
	RETIRED,

	/** 수집이 중단됐거나 검증 게이트를 통과하지 못했다. 사유는 failure_reason에 */
	FAILED,

	/** 시험 실행(일부 항목)으로 끝났다. 화면에 나가지 않고 정리 대상이다. 사유는 failure_reason에 */
	TRIAL

}
