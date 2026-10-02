package dev.chhun.hospitalcompare.hospital.repository;

import dev.chhun.hospitalcompare.hospital.entity.Snapshot;
import dev.chhun.hospitalcompare.hospital.entity.SnapshotStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

	/** 상태별 스냅샷은 하나뿐이어야 한다. 둘 이상이면 예외가 난다. */
	Optional<Snapshot> findByStatus(SnapshotStatus status);

	/** 같은 상태가 여럿일 수 있는 RETIRED·FAILED·STAGE 조회용. 최신이 먼저 */
	List<Snapshot> findByStatusOrderByIdDesc(SnapshotStatus status);

}
