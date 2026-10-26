package dev.chhun.hospitalcompare.detail.repository;

import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotRowCleaner;
import java.util.Collection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 상세 스냅샷의 행을 지운다. 스냅샷당 세부정보 약 2,400행 + 진료과목 약 4만 행이라 한 트랜잭션으로 지운다. */
@Repository
public class DetailRowCleaner implements SnapshotRowCleaner {

	private final JdbcTemplate jdbcTemplate;

	public DetailRowCleaner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public SnapshotSource source() {
		return SnapshotSource.HOSPITAL_DETAIL;
	}

	@Override
	@Transactional
	public int deleteRows(Collection<Long> snapshotIds) {
		int deleted = 0;
		for (Long snapshotId : snapshotIds) {
			deleted += jdbcTemplate.update("DELETE FROM hospital_subject WHERE snapshot_id = ?", snapshotId);
			deleted += jdbcTemplate.update("DELETE FROM hospital_detail WHERE snapshot_id = ?", snapshotId);
		}
		return deleted;
	}

}
