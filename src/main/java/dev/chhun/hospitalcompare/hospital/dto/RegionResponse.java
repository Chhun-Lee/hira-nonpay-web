package dev.chhun.hospitalcompare.hospital.dto;

import java.util.List;

/**
 * 지도를 옮길 지역. 중심은 active 스냅샷에서 좌표가 있는 기관의 위경도 평균이다.
 */
public record RegionResponse(
		String sidoCdNm,
		String sgguCd,
		String sgguCdNm,
		double latitude,
		double longitude,
		List<Dong> dongs) {

	public record Dong(String name, double latitude, double longitude) {
	}

}
