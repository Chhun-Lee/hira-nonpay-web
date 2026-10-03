package dev.chhun.hospitalcompare.snapshot.repository;

import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

	/** 출처마다 ACTIVE는 하나뿐이어야 한다. 둘 이상이면 예외가 난다. */
	Optional<Snapshot> findBySourceAndStatus(SnapshotSource source, SnapshotStatus status);

	/** 같은 상태가 여럿일 수 있는 RETIRED·FAILED·STAGE·TRIAL 조회용. 최신이 먼저 */
	List<Snapshot> findBySourceAndStatusOrderByIdDesc(SnapshotSource source, SnapshotStatus status);

	List<Snapshot> findBySource(SnapshotSource source);

}
