package dev.chhun.hospitalcompare.detail.entity;

import dev.chhun.hospitalcompare.global.jpa.NoCheckEnumJavaType;

/** HospitalDetail의 점심 종류용. 설명은 {@link NoCheckEnumJavaType} */
public class LunchKindJavaType extends NoCheckEnumJavaType<LunchKind> {

	public LunchKindJavaType() {
		super(LunchKind.class);
	}

}
