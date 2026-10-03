package dev.chhun.hospitalcompare.snapshot.entity;

import dev.chhun.hospitalcompare.global.jpa.NoCheckEnumJavaType;

/** Snapshot.source용. 설명은 {@link NoCheckEnumJavaType} */
public class SnapshotSourceJavaType extends NoCheckEnumJavaType<SnapshotSource> {

	public SnapshotSourceJavaType() {
		super(SnapshotSource.class);
	}

}
