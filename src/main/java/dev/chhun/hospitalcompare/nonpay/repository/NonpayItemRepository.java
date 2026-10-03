package dev.chhun.hospitalcompare.nonpay.repository;

import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemRecord;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 항목 코드를 JDBC batch로 upsert한다. 멱등 키는 (snapshot_id, npay_cd)다. */
@Repository
public class NonpayItemRepository {

	private static final String UPSERT = """
			INSERT INTO nonpay_item (snapshot_id, npay_cd, npay_kor_nm, mdiv_cd, mdiv_cd_nm, sdiv_cd, sdiv_cd_nm,
			                         dtl_div_cd, dtl_div_cd_nm, adt_fr_dd, adt_end_dd)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS new
			ON DUPLICATE KEY UPDATE
			    npay_kor_nm = new.npay_kor_nm, mdiv_cd = new.mdiv_cd, mdiv_cd_nm = new.mdiv_cd_nm,
			    sdiv_cd = new.sdiv_cd, sdiv_cd_nm = new.sdiv_cd_nm, dtl_div_cd = new.dtl_div_cd,
			    dtl_div_cd_nm = new.dtl_div_cd_nm, adt_fr_dd = new.adt_fr_dd, adt_end_dd = new.adt_end_dd
			""";

	private final JdbcTemplate jdbcTemplate;

	public NonpayItemRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void upsertAll(long snapshotId, List<NonpayItemRecord> records) {
		if (records.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(UPSERT, records, records.size(), (ps, r) -> {
			ps.setLong(1, snapshotId);
			ps.setString(2, r.npayCd());
			ps.setString(3, r.npayKorNm());
			ps.setString(4, r.mdivCd());
			ps.setString(5, r.mdivCdNm());
			ps.setString(6, r.sdivCd());
			ps.setString(7, r.sdivCdNm());
			ps.setString(8, r.dtlDivCd());
			ps.setString(9, r.dtlDivCdNm());
			ps.setObject(10, r.adtFrDd());
			ps.setObject(11, r.adtEndDd());
		});
	}

	public long countBySnapshot(long snapshotId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM nonpay_item WHERE snapshot_id = ?", Long.class, snapshotId);
		return count == null ? 0 : count;
	}

}
