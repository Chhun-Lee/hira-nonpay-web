package dev.chhun.hospitalcompare.nonpay.dto;

/**
 * 반경 안에서 한 항목의 가격을 공개한 병원 하나. 병원 정보는 병원 목록 ACTIVE에서, 가격·시도 코드는 비급여 ACTIVE에서 온다.
 *
 * @param sidoCd 비급여 가격 행의 시도 코드(통계와 같은 체계). 우리 시도 기준값을 정할 때 쓴다
 */
public record NearbyPrice(
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
		String sidoCd) {
}
