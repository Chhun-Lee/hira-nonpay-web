package dev.chhun.hospitalcompare.hospital.service;

import dev.chhun.hospitalcompare.detail.config.HospitalLevelProperties;
import dev.chhun.hospitalcompare.detail.dto.OpenNowSummary;
import dev.chhun.hospitalcompare.detail.dto.OpenState;
import dev.chhun.hospitalcompare.detail.dto.OpenStatus;
import dev.chhun.hospitalcompare.detail.service.OpenStatusService;
import dev.chhun.hospitalcompare.hospital.dto.HospitalSearchResponse;
import dev.chhun.hospitalcompare.hospital.dto.NearbyHospital;
import dev.chhun.hospitalcompare.hospital.dto.RegionResponse;
import dev.chhun.hospitalcompare.hospital.dto.SnapshotResponse;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.hospital.repository.HospitalQueryRepository;
import dev.chhun.hospitalcompare.hospital.repository.RegionQueryRepository;
import dev.chhun.hospitalcompare.snapshot.repository.SnapshotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
	private final OpenStatusService openStatusService;
	private final HospitalLevelProperties hospitalLevel;

	public HospitalQueryService(SnapshotRepository snapshotRepository, HospitalQueryRepository hospitalQueryRepository,
			RegionQueryRepository regionQueryRepository, OpenStatusService openStatusService,
			HospitalLevelProperties hospitalLevel) {
		this.snapshotRepository = snapshotRepository;
		this.hospitalQueryRepository = hospitalQueryRepository;
		this.regionQueryRepository = regionQueryRepository;
		this.openStatusService = openStatusService;
		this.hospitalLevel = hospitalLevel;
	}

	public HospitalSearchResponse searchNearby(double latitude, double longitude, int radiusMeters, String clCd) {
		return searchNearby(latitude, longitude, radiusMeters, clCd, false);
	}

	/**
	 * 반경 안 기관을 거리순으로 최대 500곳. 병원급 행에는 지금 상태를 붙인다.
	 *
	 * @param openNow true면 지금 진료 중인 병원급만 남긴다. 판정은 500곳으로 자르기 전에 한다
	 */
	public HospitalSearchResponse searchNearby(double latitude, double longitude, int radiusMeters, String clCd,
			boolean openNow) {
		LocalDateTime at = openStatusService.now();
		Optional<Snapshot> snapshot = active();
		if (snapshot.isEmpty()) {
			return new HospitalSearchResponse(null, List.of(), openNow ? new OpenNowSummary(at, 0, 0) : null);
		}
		long snapshotId = snapshot.get().getId();
		if (!openNow) {
			List<NearbyHospital> hospitals = hospitalQueryRepository.findNearby(snapshotId, latitude, longitude,
					radiusMeters, clCd, MAX_RESULTS);
			Map<String, OpenStatus> statuses = openStatusService.statuses(hospitals.stream()
					.filter(hospital -> hospitalLevel.isHospitalLevel(hospital.clCd()))
					.map(NearbyHospital::ykiho)
					.toList(), at);
			return new HospitalSearchResponse(snapshot.get().getBaseDate(), hospitals.stream()
					.map(hospital -> hospital.withOpenStatus(statuses.get(hospital.ykiho())))
					.toList(), null);
		}
		List<NearbyHospital> candidates = hospitalQueryRepository.findNearbyAmong(snapshotId, latitude, longitude,
				radiusMeters, clCd, hospitalLevel.levelClCodes());
		Map<String, OpenStatus> statuses = openStatusService.statuses(
				candidates.stream().map(NearbyHospital::ykiho).toList(), at);
		List<NearbyHospital> open = candidates.stream()
				.map(hospital -> hospital.withOpenStatus(statuses.get(hospital.ykiho())))
				.filter(hospital -> hospital.openStatus().status() == OpenState.OPEN)
				.toList();
		int unknown = (int) statuses.values().stream().filter(status -> status.status() == OpenState.UNKNOWN).count();
		return new HospitalSearchResponse(snapshot.get().getBaseDate(), open.stream().limit(MAX_RESULTS).toList(),
				new OpenNowSummary(at, open.size(), unknown));
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
