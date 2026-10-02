package dev.chhun.hospitalcompare.hospital.dto;

public record NearbyHospital(
		String ykiho,
		String name,
		String clCd,
		String clCdNm,
		String address,
		String phone,
		double latitude,
		double longitude,
		Integer doctorCount,
		int distanceMeters) {
}
