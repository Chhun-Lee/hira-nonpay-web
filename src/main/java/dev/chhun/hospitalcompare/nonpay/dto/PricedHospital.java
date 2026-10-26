package dev.chhun.hospitalcompare.nonpay.dto;

import dev.chhun.hospitalcompare.detail.dto.OpenStatus;

/**
 * 가격 비교 목록의 병원 하나. 가격은 원이다.
 *
 * @param openStatus 지금 진료 상태
 */
public record PricedHospital(
		String ykiho,
		String name,
		String clCd,
		String clCdNm,
		String address,
		String phone,
		double latitude,
		double longitude,
		int distanceMeters,
		long minPrice,
		long maxPrice,
		OpenStatus openStatus) {

	public static PricedHospital of(NearbyPrice price, OpenStatus openStatus) {
		return new PricedHospital(price.ykiho(), price.name(), price.clCd(), price.clCdNm(), price.address(),
				price.phone(), price.latitude(), price.longitude(), price.distanceMeters(), price.minPrice(),
				price.maxPrice(), openStatus);
	}

}
