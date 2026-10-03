package dev.chhun.hospitalcompare.nonpay.repository;

import dev.chhun.hospitalcompare.nonpay.dto.NonpayPriceRecord;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 병원 가격을 JDBC batch로 upsert한다. 멱등 키는 (snapshot_id, npay_cd, ykiho)다. 페이지 하나가 묶음 하나다. */
@Repository
public class NonpayPriceRepository {

	private static final String UPSERT = """
			INSERT INTO nonpay_price (snapshot_id, npay_cd, ykiho, min_price, max_price, cl_cd, sido_cd, sggu_cd,
			                          adt_fr_dd)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) AS new
			ON DUPLICATE KEY UPDATE
			    min_price = new.min_price, max_price = new.max_price, cl_cd = new.cl_cd,
			    sido_cd = new.sido_cd, sggu_cd = new.sggu_cd, adt_fr_dd = new.adt_fr_dd
			""";

	private final JdbcTemplate jdbcTemplate;

	public NonpayPriceRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void upsertAll(long snapshotId, List<NonpayPriceRecord> records) {
		if (records.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(UPSERT, records, records.size(), (ps, r) -> {
			ps.setLong(1, snapshotId);
			ps.setString(2, r.npayCd());
			ps.setString(3, r.ykiho());
			ps.setLong(4, r.minPrice());
			ps.setLong(5, r.maxPrice());
			ps.setString(6, r.clCd());
			ps.setString(7, r.sidoCd());
			ps.setString(8, r.sgguCd());
			ps.setObject(9, r.adtFrDd());
		});
	}

	public long countBySnapshot(long snapshotId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM nonpay_price WHERE snapshot_id = ?", Long.class, snapshotId);
		return count == null ? 0 : count;
	}

	/**
	 * 이 비급여 스냅샷의 병원 중 병원 목록 스냅샷에 없는 병원 수(폐업 등). 비교 화면에서 지도에 올리지 못한다.
	 * hospital의 유니크 키(ykiho, snapshot_id)로 한 건씩 찾는다.
	 */
	public long countUnmatchedYkiho(long nonpaySnapshotId, long hospitalSnapshotId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(DISTINCT p.ykiho) FROM nonpay_price p
				WHERE p.snapshot_id = ?
				  AND NOT EXISTS (SELECT 1 FROM hospital h WHERE h.ykiho = p.ykiho AND h.snapshot_id = ?)
				""", Long.class, nonpaySnapshotId, hospitalSnapshotId);
		return count == null ? 0 : count;
	}

}
