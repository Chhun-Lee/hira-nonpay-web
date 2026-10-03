package dev.chhun.hospitalcompare.snapshot.entity;

import dev.chhun.hospitalcompare.global.jpa.NoCheckEnumJavaType;

/** DataQualityIssue.issueType용. 설명은 {@link NoCheckEnumJavaType} */
public class QualityIssueTypeJavaType extends NoCheckEnumJavaType<QualityIssueType> {

	public QualityIssueTypeJavaType() {
		super(QualityIssueType.class);
	}

}
