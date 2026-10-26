package dev.chhun.hospitalcompare.detail.service;

import dev.chhun.hospitalcompare.detail.config.HospitalLevelProperties;
import dev.chhun.hospitalcompare.detail.dto.DetailView;
import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.HospitalDetailResponse;
import dev.chhun.hospitalcompare.detail.dto.SubjectView;
import dev.chhun.hospitalcompare.detail.repository.DetailQueryRepository;
import dev.chhun.hospitalcompare.hospital.dto.HospitalBasic;
import dev.chhun.hospitalcompare.hospital.repository.HospitalQueryRepository;
import dev.chhun.hospitalcompare.nonpay.dto.HospitalNonpayItem;
import dev.chhun.hospitalcompare.nonpay.repository.NonpayQueryRepository;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.repository.SnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 병원 상세(F4). 세 출처(병원 목록, 상세, 비급여)의 ACTIVE를 한 읽기 트랜잭션에서 읽어 ykiho로 모은다.
 */
@Service
@Transactional(readOnly = true)
public class HospitalDetailService {

	private final SnapshotRepository snapshotRepository;
	private final HospitalQueryRepository hospitalQueryRepository;
	private final DetailQueryRepository detailQueryRepository;
	private final NonpayQueryRepository nonpayQueryRepository;
	private final OpenStatusService openStatusService;
	private final HospitalLevelProperties hospitalLevel;

	public HospitalDetailService(SnapshotRepository snapshotRepository,
			HospitalQueryRepository hospitalQueryRepository, DetailQueryRepository detailQueryRepository,
			NonpayQueryRepository nonpayQueryRepository, OpenStatusService openStatusService,
			HospitalLevelProperties hospitalLevel) {
		this.snapshotRepository = snapshotRepository;
		this.hospitalQueryRepository = hospitalQueryRepository;
		this.detailQueryRepository = detailQueryRepository;
		this.nonpayQueryRepository = nonpayQueryRepository;
		this.openStatusService = openStatusService;
		this.hospitalLevel = hospitalLevel;
	}

	/** 상세 ACTIVE 기준일. 없으면 empty */
	public Optional<LocalDate> activeBaseDate() {
		return openStatusService.activeDetail().map(Snapshot::getBaseDate);
	}

	/** @return 병원 목록 ACTIVE에 없는 ykiho면 empty */
	public Optional<HospitalDetailResponse> find(String ykiho) {
		Optional<Snapshot> hospitals = active(SnapshotSource.HOSPITAL_LIST);
		Optional<HospitalBasic> found = hospitals
				.flatMap(snapshot -> hospitalQueryRepository.findBasic(snapshot.getId(), ykiho));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		HospitalBasic basic = found.get();
		boolean level = hospitalLevel.isHospitalLevel(basic.clCd());
		Optional<Snapshot> detailSnapshot = openStatusService.activeDetail();
		HospitalDetailRecord detail = detailSnapshot
				.flatMap(snapshot -> detailQueryRepository.findDetail(snapshot.getId(), ykiho))
				.orElse(null);
		List<SubjectView> subjects = detailSnapshot
				.map(snapshot -> detailQueryRepository.findSubjects(snapshot.getId(), ykiho))
				.orElseGet(List::of)
				.stream()
				.map(SubjectView::of)
				.toList();
		Optional<Snapshot> nonpay = active(SnapshotSource.NONPAY);
		List<HospitalNonpayItem> items = nonpay
				.map(snapshot -> nonpayQueryRepository.findHospitalItems(snapshot.getId(), ykiho))
				.orElseGet(List::of);
		LocalDateTime at = openStatusService.now();
		return Optional.of(new HospitalDetailResponse(basic.ykiho(), basic.name(), basic.clCd(), basic.clCdNm(),
				basic.address(), basic.phone(), basic.latitude(), basic.longitude(), basic.doctorCount(), level,
				level ? openStatusService.status(detail, at) : null, at,
				detail == null ? null : DetailView.of(detail), subjects, items,
				new HospitalDetailResponse.BaseDates(hospitals.get().getBaseDate(),
						detailSnapshot.map(Snapshot::getBaseDate).orElse(null),
						nonpay.map(Snapshot::getBaseDate).orElse(null))));
	}

	private Optional<Snapshot> active(SnapshotSource source) {
		return snapshotRepository.findBySourceAndStatus(source, SnapshotStatus.ACTIVE);
	}

}
