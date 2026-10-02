package dev.chhun.hospitalcompare.hospital;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 조회는 active 스냅샷만 읽는다. 스냅샷 조회와 기관 조회를 한 읽기 트랜잭션으로 묶어 같은 시점을 본다.
 */
@Service
@Transactional(readOnly = true)
public class HospitalQueryService {

	/** 한 번에 돌려주는 최대 기관 수 */
	static final int MAX_RESULTS = 100;

	private final SnapshotRepository snapshotRepository;
	private final HospitalQueryRepository hospitalQueryRepository;

	public HospitalQueryService(SnapshotRepository snapshotRepository, HospitalQueryRepository hospitalQueryRepository) {
		this.snapshotRepository = snapshotRepository;
		this.hospitalQueryRepository = hospitalQueryRepository;
	}

	public HospitalSearchResponse searchNearby(double latitude, double longitude, int radiusMeters, String clCd) {
		return snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)
				.map(snapshot -> new HospitalSearchResponse(snapshot.getBaseDate(),
						hospitalQueryRepository.findNearby(snapshot.getId(), latitude, longitude, radiusMeters, clCd,
								MAX_RESULTS)))
				.orElseGet(() -> new HospitalSearchResponse(null, List.of()));
	}

	public Optional<SnapshotResponse> activeSnapshot() {
		return snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)
				.map(snapshot -> new SnapshotResponse(snapshot.getBaseDate(), snapshot.getRecordCount()));
	}

}
