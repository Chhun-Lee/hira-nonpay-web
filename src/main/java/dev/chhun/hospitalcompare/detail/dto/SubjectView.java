package dev.chhun.hospitalcompare.detail.dto;

/** 진료과목 하나(화면용). 전문의 수를 모르면 null */
public record SubjectView(String code, String name, Integer specialists) {

	public static SubjectView of(HospitalSubjectRecord record) {
		return new SubjectView(record.code(), record.name(), record.specialists());
	}

}
