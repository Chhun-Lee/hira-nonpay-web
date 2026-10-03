package dev.chhun.hospitalcompare.nonpay.repository;

import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotRowCleaner;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 비급여 스냅샷의 행을 지운다. 가격은 스냅샷당 수십만 행이라 나눠 지운다.
 * 한 문장으로 지우면 undo 로그가 커지고 잠금이 길어진다. 나눈 삭제는 각자 커밋된다(트랜잭션으로 묶지 않는다).
 */
@Repository
public class NonpayRowCleaner implements SnapshotRowCleaner {

	private final JdbcTemplate jdbcTemplate;
	private final int chunkSize;

	public NonpayRowCleaner(JdbcTemplate jdbcTemplate,
			@Value("${collector.nonpay.delete-chunk-size:10000}") int chunkSize) {
		if (chunkSize < 1) {
			throw new IllegalStateException("collector.nonpay.delete-chunk-size는 1 이상이어야 합니다");
		}
		this.jdbcTemplate = jdbcTemplate;
		this.chunkSize = chunkSize;
	}

	@Override
	public SnapshotSource source() {
		return SnapshotSource.NONPAY;
	}

	@Override
	public int deleteRows(Collection<Long> snapshotIds) {
		int deleted = 0;
		for (Long snapshotId : snapshotIds) {
			int chunk;
			do {
				chunk = jdbcTemplate.update("DELETE FROM nonpay_price WHERE snapshot_id = ? LIMIT " + chunkSize,
						snapshotId);
				deleted += chunk;
			} while (chunk == chunkSize);
			deleted += jdbcTemplate.update("DELETE FROM nonpay_item WHERE snapshot_id = ?", snapshotId);
			deleted += jdbcTemplate.update("DELETE FROM nonpay_stat WHERE snapshot_id = ?", snapshotId);
		}
		return deleted;
	}

}
