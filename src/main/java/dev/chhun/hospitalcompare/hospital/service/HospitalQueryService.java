package dev.chhun.hospitalcompare.hospital.service;

import dev.chhun.hospitalcompare.hospital.dto.HospitalSearchResponse;
import dev.chhun.hospitalcompare.hospital.dto.RegionResponse;
import dev.chhun.hospitalcompare.hospital.dto.SnapshotResponse;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.hospital.repository.HospitalQueryRepository;
import dev.chhun.hospitalcompare.hospital.repository.RegionQueryRepository;
import dev.chhun.hospitalcompare.snapshot.repository.SnapshotRepository;
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
	public static final int MAX_RESULTS = 500;

	private final SnapshotRepository snapshotRepository;
	private final HospitalQueryRepository hospitalQueryRepository;
	private final RegionQueryRepository regionQueryRepository;

	public HospitalQueryService(SnapshotRepository snapshotRepository, HospitalQueryRepository hospitalQueryRepository,
			RegionQueryRepository regionQueryRepository) {
		this.snapshotRepository = snapshotRepository;
		this.hospitalQueryRepository = hospitalQueryRepository;
		this.regionQueryRepository = regionQueryRepository;
	}

	public HospitalSearchResponse searchNearby(double latitude, double longitude, int radiusMeters, String clCd) {
		return active()
				.map(snapshot -> new HospitalSearchResponse(snapshot.getBaseDate(),
						hospitalQueryRepository.findNearby(snapshot.getId(), latitude, longitude, radiusMeters, clCd,
								MAX_RESULTS)))
				.orElseGet(() -> new HospitalSearchResponse(null, List.of()));
	}

	public Optional<SnapshotResponse> activeSnapshot() {
		return active()
				.map(snapshot -> new SnapshotResponse(snapshot.getBaseDate(), snapshot.getRecordCount()));
	}

	public List<RegionResponse> regions() {
		return active()
				.map(snapshot -> regionQueryRepository.findRegions(snapshot.getId()))
				.orElseGet(List::of);
	}

	/** 화면은 병원 목록 출처의 ACTIVE만 읽는다. 비급여 스냅샷과 섞이지 않는다. */
	private Optional<Snapshot> active() {
		return snapshotRepository.findBySourceAndStatus(SnapshotSource.HOSPITAL_LIST, SnapshotStatus.ACTIVE);
	}

}
