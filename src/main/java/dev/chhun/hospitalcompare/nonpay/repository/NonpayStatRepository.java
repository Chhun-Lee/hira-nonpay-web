package dev.chhun.hospitalcompare.nonpay.repository;

import dev.chhun.hospitalcompare.nonpay.dto.NonpayStatRecord;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 기준값을 JDBC batch로 upsert한다. 멱등 키는 (snapshot_id, npay_cd, dimension, dim_key)다. */
@Repository
public class NonpayStatRepository {

	private static final String UPSERT = """
			INSERT INTO nonpay_stat (snapshot_id, npay_cd, dimension, dim_key, min_price, max_price, avg_price,
			                         median_price, std_date)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) AS new
			ON DUPLICATE KEY UPDATE
			    min_price = new.min_price, max_price = new.max_price, avg_price = new.avg_price,
			    median_price = new.median_price, std_date = new.std_date
			""";

	private final JdbcTemplate jdbcTemplate;

	public NonpayStatRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void upsertAll(long snapshotId, List<NonpayStatRecord> records) {
		if (records.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(UPSERT, records, records.size(), (ps, r) -> {
			ps.setLong(1, snapshotId);
			ps.setString(2, r.npayCd());
			ps.setString(3, r.dimension().name());
			ps.setString(4, r.dimKey());
			ps.setObject(5, r.minPrice());
			ps.setObject(6, r.maxPrice());
			ps.setObject(7, r.avgPrice());
			ps.setObject(8, r.medianPrice());
			ps.setObject(9, r.stdDate());
		});
	}

	public long countBySnapshot(long snapshotId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM nonpay_stat WHERE snapshot_id = ?", Long.class, snapshotId);
		return count == null ? 0 : count;
	}

}
