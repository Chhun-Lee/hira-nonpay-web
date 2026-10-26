package dev.chhun.hospitalcompare.hospital.dto;

/**
 * 병원 목록 ACTIVE의 기관 하나(상세 화면 머리용). 좌표가 없는 기관이 있어 위경도는 nullable이다.
 */
public record HospitalBasic(String ykiho, String name, String clCd, String clCdNm, String address, String phone,
		Double latitude, Double longitude, Integer doctorCount) {
}
