package dev.chhun.hospitalcompare.hospital;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 시군구·동 목록과 중심 좌표. 지역 코드 데이터를 따로 받지 않고 수집한 기관 좌표로 계산한다.
 */
@Repository
public class RegionQueryRepository {

	// 좌표가 있는 기관을 동 단위로 묶는다. 읍면동명이 없는 기관도 시군구 평균에는 들어가도록 null 그룹을 남긴다.
	private static final String DONG_CENTERS = """
			SELECT sido_cd_nm, sggu_cd, sggu_cd_nm, emdong_nm,
			       AVG(latitude) AS latitude, AVG(longitude) AS longitude, COUNT(*) AS hospital_count
			FROM hospital
			WHERE snapshot_id = :snapshotId
			  AND sggu_cd IS NOT NULL
			  AND latitude IS NOT NULL
			  AND longitude IS NOT NULL
			GROUP BY sido_cd_nm, sggu_cd, sggu_cd_nm, emdong_nm
			""";

	private static final Comparator<RegionResponse> REGION_ORDER = Comparator
			.comparing(RegionResponse::sidoCdNm, Comparator.nullsLast(Comparator.<String>naturalOrder()))
			.thenComparing(RegionResponse::sgguCdNm, Comparator.nullsLast(Comparator.<String>naturalOrder()));

	private final JdbcClient jdbcClient;

	public RegionQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<RegionResponse> findRegions(long snapshotId) {
		List<DongCenter> dongCenters = jdbcClient.sql(DONG_CENTERS)
				.param("snapshotId", snapshotId)
				.query((rs, rowNum) -> new DongCenter(
						rs.getString("sido_cd_nm"),
						rs.getString("sggu_cd"),
						rs.getString("sggu_cd_nm"),
						rs.getString("emdong_nm"),
						rs.getDouble("latitude"),
						rs.getDouble("longitude"),
						rs.getLong("hospital_count")))
				.list();

		Map<String, List<DongCenter>> bySggu = dongCenters.stream()
				.collect(Collectors.groupingBy(DongCenter::sgguCd, LinkedHashMap::new, Collectors.toList()));
		return bySggu.values().stream()
				.map(RegionQueryRepository::toRegion)
				.sorted(REGION_ORDER)
				.toList();
	}

	/** 시군구 중심은 동별 평균을 기관 수로 가중 평균한 값, 즉 시군구 전체 기관의 평균이다. */
	private static RegionResponse toRegion(List<DongCenter> dongCenters) {
		DongCenter any = dongCenters.getFirst();
		long total = dongCenters.stream().mapToLong(DongCenter::hospitalCount).sum();
		double latitude = dongCenters.stream().mapToDouble(d -> d.latitude() * d.hospitalCount()).sum() / total;
		double longitude = dongCenters.stream().mapToDouble(d -> d.longitude() * d.hospitalCount()).sum() / total;
		List<RegionResponse.Dong> dongs = dongCenters.stream()
				.filter(d -> d.emdongNm() != null && !d.emdongNm().isBlank())
				.sorted(Comparator.comparing(DongCenter::emdongNm))
				.map(d -> new RegionResponse.Dong(d.emdongNm(), d.latitude(), d.longitude()))
				.toList();
		return new RegionResponse(any.sidoCdNm(), any.sgguCd(), any.sgguCdNm(), latitude, longitude, dongs);
	}

	private record DongCenter(String sidoCdNm, String sgguCd, String sgguCdNm, String emdongNm,
			double latitude, double longitude, long hospitalCount) {
	}

}
