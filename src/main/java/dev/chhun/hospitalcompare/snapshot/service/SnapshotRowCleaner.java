package dev.chhun.hospitalcompare.snapshot.service;

import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import java.util.Collection;

/**
 * 출처마다 하나. 정리 대상 스냅샷의 데이터 행을 지운다. snapshot 패키지가 각 도메인의 테이블을 몰라도 되게 한다.
 */
public interface SnapshotRowCleaner {

	SnapshotSource source();

	/** @return 지운 행 수 */
	int deleteRows(Collection<Long> snapshotIds);

}
