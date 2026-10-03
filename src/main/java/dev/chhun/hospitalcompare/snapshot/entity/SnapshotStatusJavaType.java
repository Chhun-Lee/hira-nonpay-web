package dev.chhun.hospitalcompare.snapshot.entity;

import dev.chhun.hospitalcompare.global.jpa.NoCheckEnumJavaType;

/** Snapshot.status용. 설명은 {@link NoCheckEnumJavaType} */
public class SnapshotStatusJavaType extends NoCheckEnumJavaType<SnapshotStatus> {

	public SnapshotStatusJavaType() {
		super(SnapshotStatus.class);
	}

}
