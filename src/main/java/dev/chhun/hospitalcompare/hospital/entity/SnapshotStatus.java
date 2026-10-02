package dev.chhun.hospitalcompare.hospital.entity;

public enum SnapshotStatus {

	/** 적재 중. 화면에 노출하지 않는다. */
	STAGE,

	/** 사용자에게 보여 주는 스냅샷. 한 번에 하나만 둔다. */
	ACTIVE,

	/** 새 스냅샷으로 교체된 이전 버전 */
	RETIRED

}
