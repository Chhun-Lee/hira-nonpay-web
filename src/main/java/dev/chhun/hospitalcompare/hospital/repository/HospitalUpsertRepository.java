package dev.chhun.hospitalcompare.hospital.repository;

import dev.chhun.hospitalcompare.hospital.dto.HospitalRecord;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotRowCleaner;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 결과를 JDBC batch로 upsert한다. 멱등 키는 uk_hospital_ykiho_snapshot(ykiho, snapshot_id)이다.
 */
@Repository
public class HospitalUpsertRepository implements SnapshotRowCleaner {

	// AS new는 MySQL 8.0.19부터 쓰는 행 별칭이다. 갱신 값에 쓰던 VALUES()는 8.0.20부터 deprecated.
	private static final String UPSERT = """
			INSERT INTO hospital (ykiho, snapshot_id, name, cl_cd, cl_cd_nm, sido_cd, sido_cd_nm, sggu_cd, sggu_cd_nm,
			                      emdong_nm, address, phone, established_date, latitude, longitude, doctor_count)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS new
			ON DUPLICATE KEY UPDATE
			    name = new.name, cl_cd = new.cl_cd, cl_cd_nm = new.cl_cd_nm,
			    sido_cd = new.sido_cd, sido_cd_nm = new.sido_cd_nm, sggu_cd = new.sggu_cd, sggu_cd_nm = new.sggu_cd_nm,
			    emdong_nm = new.emdong_nm, address = new.address, phone = new.phone,
			    established_date = new.established_date, latitude = new.latitude, longitude = new.longitude,
			    doctor_count = new.doctor_count
			""";

	private final JdbcTemplate jdbcTemplate;

	public HospitalUpsertRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void upsertAll(long snapshotId, List<HospitalRecord> records) {
		if (records.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				HospitalRecord record = records.get(i);
				ps.setString(1, record.ykiho());
				ps.setLong(2, snapshotId);
				ps.setString(3, record.name());
				ps.setString(4, record.clCd());
				ps.setString(5, record.clCdNm());
				ps.setString(6, record.sidoCd());
				ps.setString(7, record.sidoCdNm());
				ps.setString(8, record.sgguCd());
				ps.setString(9, record.sgguCdNm());
				ps.setString(10, record.emdongNm());
				ps.setString(11, record.address());
				ps.setString(12, record.phone());
				ps.setObject(13, record.establishedDate());
				ps.setObject(14, record.latitude());
				ps.setObject(15, record.longitude());
				ps.setObject(16, record.doctorCount());
			}

			@Override
			public int getBatchSize() {
				return records.size();
			}

		});
	}

	public long countBySnapshot(long snapshotId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM hospital WHERE snapshot_id = ?", Long.class, snapshotId);
		return count == null ? 0 : count;
	}

	@Override
	public SnapshotSource source() {
		return SnapshotSource.HOSPITAL_LIST;
	}

	/** 지정한 스냅샷들의 병원 행을 한 트랜잭션에서 지운다. */
	@Override
	@Transactional
	public int deleteRows(Collection<Long> snapshotIds) {
		int deleted = 0;
		for (Long snapshotId : snapshotIds) {
			deleted += jdbcTemplate.update("DELETE FROM hospital WHERE snapshot_id = ?", snapshotId);
		}
		return deleted;
	}

}
