package dev.chhun.hospitalcompare.hospital.service;

import dev.chhun.hospitalcompare.hospital.entity.Snapshot;
import dev.chhun.hospitalcompare.hospital.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.hospital.repository.HospitalUpsertRepository;
import dev.chhun.hospitalcompare.hospital.repository.SnapshotRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스냅샷 생애: STAGE 적재 → 검증을 통과하면 ACTIVE(기존 ACTIVE는 RETIRED), 아니면 FAILED.
 * 화면은 ACTIVE만 읽고, 전환은 한 트랜잭션이라 섞여 보이지 않는다.
 */
@Service
public class SnapshotService {

	public static final String INTERRUPTED = "중단된 수집(다음 실행 시작 시 정리)";

	private final SnapshotRepository snapshotRepository;
	private final HospitalUpsertRepository hospitalUpsertRepository;

	public SnapshotService(SnapshotRepository snapshotRepository, HospitalUpsertRepository hospitalUpsertRepository) {
		this.snapshotRepository = snapshotRepository;
		this.hospitalUpsertRepository = hospitalUpsertRepository;
	}

	/** 강제 종료로 남은 STAGE를 FAILED로 정리하고 새 STAGE를 만든다. collector는 한 번에 하나만 돈다는 전제다. */
	@Transactional
	public Snapshot startStage(LocalDate baseDate) {
		for (Snapshot leftover : snapshotRepository.findByStatusOrderByIdDesc(SnapshotStatus.STAGE)) {
			leftover.fail(INTERRUPTED);
		}
		return snapshotRepository.save(new Snapshot(baseDate, SnapshotStatus.STAGE));
	}

	@Transactional(readOnly = true)
	public Optional<Integer> activeRecordCount() {
		return snapshotRepository.findByStatus(SnapshotStatus.ACTIVE).map(Snapshot::getRecordCount);
	}

	@Transactional
	public void activate(long stageId, int recordCount) {
		snapshotRepository.findByStatus(SnapshotStatus.ACTIVE).ifPresent(Snapshot::retire);
		snapshotRepository.findById(stageId).orElseThrow().activate(recordCount);
	}

	@Transactional
	public void fail(long stageId, String reason) {
		snapshotRepository.findById(stageId).orElseThrow().fail(reason);
	}

	/**
	 * ACTIVE와 가장 최근 RETIRED만 병원 행을 남긴다. 스냅샷 기록과 품질 이슈는 지우지 않는다.
	 * 이전 RETIRED 하나는 주간 diff(3-5)와 되돌리기에 쓴다.
	 *
	 * @return 지운 병원 행 수
	 */
	public int cleanup() {
		Set<Long> keep = new HashSet<>();
		snapshotRepository.findByStatus(SnapshotStatus.ACTIVE).ifPresent(snapshot -> keep.add(snapshot.getId()));
		List<Snapshot> retired = snapshotRepository.findByStatusOrderByIdDesc(SnapshotStatus.RETIRED);
		if (!retired.isEmpty()) {
			keep.add(retired.getFirst().getId());
		}
		List<Long> drop = snapshotRepository.findAll().stream()
				.filter(snapshot -> snapshot.getStatus() == SnapshotStatus.RETIRED
						|| snapshot.getStatus() == SnapshotStatus.FAILED)
				.map(Snapshot::getId)
				.filter(id -> !keep.contains(id))
				.toList();
		return hospitalUpsertRepository.deleteBySnapshotIds(drop);
	}

}
