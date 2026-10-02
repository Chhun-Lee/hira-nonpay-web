package dev.chhun.hospitalcompare.hospital;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

	/** 상태별 스냅샷은 하나뿐이어야 한다. 둘 이상이면 예외가 난다. */
	Optional<Snapshot> findByStatus(SnapshotStatus status);

}
