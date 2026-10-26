package dev.chhun.hospitalcompare.hospital.dto;

import dev.chhun.hospitalcompare.detail.dto.OpenStatus;

/**
 * @param openStatus 지금 진료 상태. 병원급만 있고 나머지는 null
 */
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
		int distanceMeters,
		OpenStatus openStatus) {

	public NearbyHospital withOpenStatus(OpenStatus status) {
		return new NearbyHospital(ykiho, name, clCd, clCdNm, address, phone, latitude, longitude, doctorCount,
				distanceMeters, status);
	}

}
