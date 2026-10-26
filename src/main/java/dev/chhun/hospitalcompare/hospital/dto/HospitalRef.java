package dev.chhun.hospitalcompare.hospital.dto;

/** 기관 키와 종별. 상세정보 수집 대상과 종별 채움률 집계에 쓴다. */
public record HospitalRef(String ykiho, String clCd) {
}
