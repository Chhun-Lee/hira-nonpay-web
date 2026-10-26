package dev.chhun.hospitalcompare.detail.entity;

import dev.chhun.hospitalcompare.global.jpa.NoCheckEnumJavaType;

/** HospitalDetail의 휴진 안내 분류용. 설명은 {@link NoCheckEnumJavaType} */
public class NoteKindJavaType extends NoCheckEnumJavaType<NoteKind> {

	public NoteKindJavaType() {
		super(NoteKind.class);
	}

}
