package dev.chhun.hospitalcompare.nonpay.repository;

import dev.chhun.hospitalcompare.global.util.BoundingBox;
import dev.chhun.hospitalcompare.nonpay.dto.NearbyPrice;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemSummary;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayStatRecord;
import dev.chhun.hospitalcompare.nonpay.entity.StatDimension;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 비급여 조회(항목 검색, 반경 안 가격, 통계). 실행 계획을 비교하기 쉽도록 SQL을 그대로 둔다.
 * 가격은 비급여 ACTIVE에서, 병원 이름·좌표는 병원 목록 ACTIVE에서 읽어 ykiho로 잇는다(수집 주기가 달라 복사하지 않는다).
 */
@Repository
public class NonpayQueryRepository {

	// 가격을 공개한 병원이 있는 항목만 남긴다(JOIN). 검색어의 %, _ 는 ! 로 이스케이프한다.
	private static final String SEARCH_ITEMS = """
			SELECT i.npay_cd, i.npay_kor_nm, i.mdiv_cd_nm, COUNT(*) AS hospital_count
			FROM nonpay_item i
			JOIN nonpay_price p ON p.snapshot_id = i.snapshot_id AND p.npay_cd = i.npay_cd
			WHERE i.snapshot_id = :snapshotId
			  AND i.npay_kor_nm LIKE :pattern ESCAPE '!'
			GROUP BY i.npay_cd, i.npay_kor_nm, i.mdiv_cd_nm
			ORDER BY hospital_count DESC, i.npay_cd
			LIMIT :limit
			""";

	private static final String FIND_ITEMS = """
			SELECT i.npay_cd, i.npay_kor_nm, i.mdiv_cd_nm, COUNT(*) AS hospital_count
			FROM nonpay_item i
			JOIN nonpay_price p ON p.snapshot_id = i.snapshot_id AND p.npay_cd = i.npay_cd
			WHERE i.snapshot_id = :snapshotId
			  AND i.npay_cd IN (:codes)
			GROUP BY i.npay_cd, i.npay_kor_nm, i.mdiv_cd_nm
			""";

	// 가격이 없는 항목도 찾는다(공개 병원 0곳). 가격 비교에서 404와 빈 결과를 가르려고 쓴다.
	private static final String FIND_ITEM = """
			SELECT i.npay_cd, i.npay_kor_nm, i.mdiv_cd_nm,
			       (SELECT COUNT(*) FROM nonpay_price p
			        WHERE p.snapshot_id = i.snapshot_id AND p.npay_cd = i.npay_cd) AS hospital_count
			FROM nonpay_item i
			WHERE i.snapshot_id = :snapshotId
			  AND i.npay_cd = :code
			""";

	// POINT는 (경도, 위도) 순서다. 박스로 좁힌 후보에서 원 밖을 버린다.
	// 정렬·자르기는 서비스가 한다(반경 기준값을 자르기 전 전체로 계산해야 한다).
	private static final String NEARBY_PRICES = """
			SELECT ykiho, name, cl_cd, cl_cd_nm, address, phone, latitude, longitude, min_price, max_price, sido_cd,
			       distance
			FROM (
			    SELECT p.ykiho, h.name, h.cl_cd, h.cl_cd_nm, h.address, h.phone, h.latitude, h.longitude,
			           p.min_price, p.max_price, p.sido_cd,
			           ST_Distance_Sphere(POINT(h.longitude, h.latitude), POINT(:lng, :lat)) AS distance
			    FROM nonpay_price p
			    JOIN hospital h ON h.ykiho = p.ykiho AND h.snapshot_id = :hospitalSnapshotId
			    WHERE p.snapshot_id = :nonpaySnapshotId
			      AND p.npay_cd = :itemCd
			      AND h.latitude BETWEEN :minLat AND :maxLat
			      AND h.longitude BETWEEN :minLng AND :maxLng
			) candidate
			WHERE distance <= :radius
			""";

	private static final String FIND_STATS = """
			SELECT npay_cd, dimension, dim_key, min_price, max_price, avg_price, median_price, std_date
			FROM nonpay_stat
			WHERE snapshot_id = :snapshotId
			  AND npay_cd = :itemCd
			ORDER BY dimension, dim_key
			""";

	private final JdbcClient jdbcClient;

	public NonpayQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<NonpayItemSummary> searchItems(long snapshotId, String query, int limit) {
		return jdbcClient.sql(SEARCH_ITEMS)
				.param("snapshotId", snapshotId)
				.param("pattern", "%" + escapeLike(query) + "%")
				.param("limit", limit)
				.query(NonpayQueryRepository::item)
				.list();
	}

	/** 빠른 선택용. 가격을 공개한 병원이 있는 코드만 돌려준다(순서는 정하지 않는다). */
	public List<NonpayItemSummary> findItems(long snapshotId, Collection<String> codes) {
		if (codes.isEmpty()) {
			return List.of();
		}
		return jdbcClient.sql(FIND_ITEMS)
				.param("snapshotId", snapshotId)
				.param("codes", codes)
				.query(NonpayQueryRepository::item)
				.list();
	}

	public Optional<NonpayItemSummary> findItem(long snapshotId, String code) {
		return jdbcClient.sql(FIND_ITEM)
				.param("snapshotId", snapshotId)
				.param("code", code)
				.query(NonpayQueryRepository::item)
				.optional();
	}

	public List<NearbyPrice> findNearbyPrices(long nonpaySnapshotId, long hospitalSnapshotId, String itemCd,
			double latitude, double longitude, int radiusMeters) {
		BoundingBox box = BoundingBox.around(latitude, longitude, radiusMeters);
		return jdbcClient.sql(NEARBY_PRICES)
				.param("nonpaySnapshotId", nonpaySnapshotId)
				.param("hospitalSnapshotId", hospitalSnapshotId)
				.param("itemCd", itemCd)
				.param("lat", latitude)
				.param("lng", longitude)
				.param("minLat", box.minLatitude())
				.param("maxLat", box.maxLatitude())
				.param("minLng", box.minLongitude())
				.param("maxLng", box.maxLongitude())
				.param("radius", radiusMeters)
				.query((rs, rowNum) -> new NearbyPrice(
						rs.getString("ykiho"),
						rs.getString("name"),
						rs.getString("cl_cd"),
						rs.getString("cl_cd_nm"),
						rs.getString("address"),
						rs.getString("phone"),
						rs.getDouble("latitude"),
						rs.getDouble("longitude"),
						(int) Math.round(rs.getDouble("distance")),
						rs.getLong("min_price"),
						rs.getLong("max_price"),
						rs.getString("sido_cd")))
				.list();
	}

	public List<NonpayStatRecord> findStats(long snapshotId, String itemCd) {
		return jdbcClient.sql(FIND_STATS)
				.param("snapshotId", snapshotId)
				.param("itemCd", itemCd)
				.query((rs, rowNum) -> new NonpayStatRecord(
						rs.getString("npay_cd"),
						StatDimension.valueOf(rs.getString("dimension")),
						rs.getString("dim_key"),
						rs.getObject("min_price", Long.class),
						rs.getObject("max_price", Long.class),
						rs.getObject("avg_price", Long.class),
						rs.getObject("median_price", Long.class),
						rs.getObject("std_date", LocalDate.class)))
				.list();
	}

	/** LIKE 와일드카드(%, _)와 이스케이프 문자(!)를 글자 그대로 찾게 바꾼다. */
	static String escapeLike(String value) {
		return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

	private static NonpayItemSummary item(ResultSet rs, int rowNum) throws SQLException {
		return new NonpayItemSummary(
				rs.getString("npay_cd"),
				rs.getString("npay_kor_nm"),
				rs.getString("mdiv_cd_nm"),
				rs.getLong("hospital_count"));
	}

}
