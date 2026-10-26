package dev.chhun.hospitalcompare.detail.dto;

/**
 * 한 기관의 진료과목 하나.
 *
 * @param specialists 과목별 전문의 수. 모르면 null
 */
public record HospitalSubjectRecord(String ykiho, String code, String name, Integer specialists) {
}
