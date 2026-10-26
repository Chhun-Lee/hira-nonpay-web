package dev.chhun.hospitalcompare.hospital.repository;

import dev.chhun.hospitalcompare.global.util.BoundingBox;
import dev.chhun.hospitalcompare.hospital.dto.HospitalRef;
import dev.chhun.hospitalcompare.hospital.dto.NearbyHospital;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 반경 검색. 공간 인덱스 없이 바운딩 박스 범위 조건 + ST_Distance_Sphere로 계산한다.
 * 실행 계획을 비교하기 쉽도록 SQL을 그대로 둔다.
 */
@Repository
public class HospitalQueryRepository {

	// POINT는 (경도, 위도) 순서다. 박스로 좁힌 후보에서 원 밖을 버리고 거리순으로 자른다.
	private static final String NEARBY = """
			SELECT ykiho, name, cl_cd, cl_cd_nm, address, phone, latitude, longitude, doctor_count, distance
			FROM (
			    SELECT ykiho, name, cl_cd, cl_cd_nm, address, phone, latitude, longitude, doctor_count,
			           ST_Distance_Sphere(POINT(longitude, latitude), POINT(:lng, :lat)) AS distance
			    FROM hospital
			    WHERE snapshot_id = :snapshotId
			      AND latitude BETWEEN :minLat AND :maxLat
			      AND longitude BETWEEN :minLng AND :maxLng
			      %s
			) candidate
			WHERE distance <= :radius
			ORDER BY distance, ykiho
			LIMIT :limit
			""";

	private final JdbcClient jdbcClient;

	public HospitalQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<NearbyHospital> findNearby(long snapshotId, double latitude, double longitude, int radiusMeters,
			String clCd, int limit) {
		BoundingBox box = BoundingBox.around(latitude, longitude, radiusMeters);
		String sql = NEARBY.formatted(clCd == null ? "" : "AND cl_cd = :clCd");

		return jdbcClient.sql(sql)
				.param("snapshotId", snapshotId)
				.param("lat", latitude)
				.param("lng", longitude)
				.param("minLat", box.minLatitude())
				.param("maxLat", box.maxLatitude())
				.param("minLng", box.minLongitude())
				.param("maxLng", box.maxLongitude())
				.param("clCd", clCd)
				.param("radius", radiusMeters)
				.param("limit", limit)
				.query((rs, rowNum) -> new NearbyHospital(
						rs.getString("ykiho"),
						rs.getString("name"),
						rs.getString("cl_cd"),
						rs.getString("cl_cd_nm"),
						rs.getString("address"),
						rs.getString("phone"),
						rs.getDouble("latitude"),
						rs.getDouble("longitude"),
						rs.getObject("doctor_count", Integer.class),
						(int) Math.round(rs.getDouble("distance"))))
				.list();
	}

	/** 상세정보 수집 대상: 종별이 clCodes 안인 기관. sgguCd가 있으면 그 시군구만. ykiho 순 */
	public List<HospitalRef> findDetailTargets(long snapshotId, Collection<String> clCodes, String sgguCd) {
		String sql = """
				SELECT ykiho, cl_cd FROM hospital
				WHERE snapshot_id = :snapshotId AND cl_cd IN (:clCodes) %s
				ORDER BY ykiho
				""".formatted(sgguCd == null ? "" : "AND sggu_cd = :sgguCd");
		return jdbcClient.sql(sql)
				.param("snapshotId", snapshotId)
				.param("clCodes", clCodes)
				.param("sgguCd", sgguCd)
				.query((rs, rowNum) -> new HospitalRef(rs.getString("ykiho"), rs.getString("cl_cd")))
				.list();
	}

}
