package dev.chhun.hospitalcompare.snapshot.service;

import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.repository.SnapshotRepository;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출처별 스냅샷 생애: STAGE 적재 → 검증을 통과하면 ACTIVE(같은 출처의 기존 ACTIVE는 RETIRED), 아니면 FAILED.
 * 시험 실행은 TRIAL로 끝난다. 화면은 ACTIVE만 읽고, 전환은 한 트랜잭션이라 섞여 보이지 않는다.
 */
@Service
public class SnapshotService {

	public static final String INTERRUPTED = "중단된 수집(다음 실행 시작 시 정리)";

	private final SnapshotRepository snapshotRepository;
	private final Map<SnapshotSource, SnapshotRowCleaner> cleaners = new EnumMap<>(SnapshotSource.class);

	public SnapshotService(SnapshotRepository snapshotRepository, List<SnapshotRowCleaner> cleaners) {
		this.snapshotRepository = snapshotRepository;
		for (SnapshotRowCleaner cleaner : cleaners) {
			if (this.cleaners.put(cleaner.source(), cleaner) != null) {
				throw new IllegalStateException("출처 하나에 정리기가 둘 이상입니다: " + cleaner.source());
			}
		}
	}

	/** 같은 출처에서 강제 종료로 남은 STAGE를 FAILED로 정리하고 새 STAGE를 만든다. 수집기는 한 번에 하나만 돈다는 전제다. */
	@Transactional
	public Snapshot startStage(SnapshotSource source, LocalDate baseDate) {
		for (Snapshot leftover : snapshotRepository.findBySourceAndStatusOrderByIdDesc(source, SnapshotStatus.STAGE)) {
			leftover.fail(INTERRUPTED);
		}
		return snapshotRepository.save(new Snapshot(source, baseDate, SnapshotStatus.STAGE));
	}

	@Transactional(readOnly = true)
	public Optional<Integer> activeRecordCount(SnapshotSource source) {
		return snapshotRepository.findBySourceAndStatus(source, SnapshotStatus.ACTIVE).map(Snapshot::getRecordCount);
	}

	@Transactional(readOnly = true)
	public Optional<Long> activeSnapshotId(SnapshotSource source) {
		return snapshotRepository.findBySourceAndStatus(source, SnapshotStatus.ACTIVE).map(Snapshot::getId);
	}

	/** STAGE를 ACTIVE로, 같은 출처의 기존 ACTIVE를 RETIRED로 한 트랜잭션에서 바꾼다. */
	@Transactional
	public void activate(long stageId, int recordCount) {
		Snapshot stage = find(stageId);
		snapshotRepository.findBySourceAndStatus(stage.getSource(), SnapshotStatus.ACTIVE).ifPresent(Snapshot::retire);
		stage.activate(recordCount);
	}

	@Transactional
	public void fail(long stageId, String reason) {
		find(stageId).fail(reason);
	}

	@Transactional
	public void endTrial(long stageId, String reason) {
		find(stageId).endTrial(reason);
	}

	/**
	 * 같은 출처의 ACTIVE와 가장 최근 RETIRED만 데이터 행을 남긴다. RETIRED·FAILED·TRIAL의 나머지 행은 그 출처의 정리기로 지운다.
	 * 다른 출처와 STAGE는 건드리지 않는다. 스냅샷 기록과 품질 이슈는 지우지 않는다. 이전 RETIRED 하나는 주간 diff(3-5)와 되돌리기에 쓴다.
	 *
	 * @return 지운 데이터 행 수
	 */
	public int cleanup(SnapshotSource source) {
		SnapshotRowCleaner cleaner = cleaners.get(source);
		if (cleaner == null) {
			throw new IllegalStateException("정리기가 없는 출처입니다: " + source);
		}
		Set<Long> keep = new HashSet<>();
		snapshotRepository.findBySourceAndStatus(source, SnapshotStatus.ACTIVE).ifPresent(snapshot -> keep.add(snapshot.getId()));
		List<Snapshot> retired = snapshotRepository.findBySourceAndStatusOrderByIdDesc(source, SnapshotStatus.RETIRED);
		if (!retired.isEmpty()) {
			keep.add(retired.getFirst().getId());
		}
		List<Long> drop = snapshotRepository.findBySource(source).stream()
				.filter(snapshot -> snapshot.getStatus() == SnapshotStatus.RETIRED
						|| snapshot.getStatus() == SnapshotStatus.FAILED
						|| snapshot.getStatus() == SnapshotStatus.TRIAL)
				.map(Snapshot::getId)
				.filter(id -> !keep.contains(id))
				.toList();
		return drop.isEmpty() ? 0 : cleaner.deleteRows(drop);
	}

	private Snapshot find(long snapshotId) {
		return snapshotRepository.findById(snapshotId)
				.orElseThrow(() -> new IllegalStateException("스냅샷이 없습니다: " + snapshotId));
	}

}
