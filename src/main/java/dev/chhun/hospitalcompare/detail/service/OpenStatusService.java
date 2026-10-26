package dev.chhun.hospitalcompare.detail.service;

import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.OpenStatus;
import dev.chhun.hospitalcompare.detail.repository.DetailQueryRepository;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.repository.SnapshotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관들의 지금 진료 상태. 상세 ACTIVE의 세부정보만 읽는다(STAGE·TRIAL은 보지 않는다).
 * 부르는 쪽의 읽기 트랜잭션에 함께 들어가 같은 시점을 본다.
 */
@Service
@Transactional(readOnly = true)
public class OpenStatusService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final SnapshotRepository snapshotRepository;
	private final DetailQueryRepository detailQueryRepository;
	private final HolidayCalendar holidayCalendar;
	private final Clock clock;

	public OpenStatusService(SnapshotRepository snapshotRepository, DetailQueryRepository detailQueryRepository,
			HolidayCalendar holidayCalendar, Clock clock) {
		this.snapshotRepository = snapshotRepository;
		this.detailQueryRepository = detailQueryRepository;
		this.holidayCalendar = holidayCalendar;
		this.clock = clock;
	}

	/** 판정 기준 시각(한국 시간, 분 단위) */
	public LocalDateTime now() {
		return LocalDateTime.now(clock.withZone(KST)).truncatedTo(ChronoUnit.MINUTES);
	}

	public Optional<Snapshot> activeDetail() {
		return snapshotRepository.findBySourceAndStatus(SnapshotSource.HOSPITAL_DETAIL, SnapshotStatus.ACTIVE);
	}

	/** 기관마다 지금 상태. 상세 ACTIVE가 없거나 세부정보가 없는 기관은 "진료시간 정보 없음"이다. */
	public Map<String, OpenStatus> statuses(Collection<String> ykihos, LocalDateTime at) {
		if (ykihos.isEmpty()) {
			return Map.of();
		}
		Map<String, HospitalDetailRecord> details = activeDetail()
				.map(snapshot -> detailQueryRepository.findDetails(snapshot.getId(), ykihos))
				.orElseGet(Map::of);
		boolean holiday = holidayCalendar.isHoliday(at.toLocalDate());
		Map<String, OpenStatus> statuses = new HashMap<>();
		for (String ykiho : ykihos) {
			statuses.put(ykiho, OpenStatusJudge.judge(details.get(ykiho), at, holiday));
		}
		return statuses;
	}

	/** 이미 읽은 세부정보(없으면 null)로 판정한다 */
	public OpenStatus status(HospitalDetailRecord detail, LocalDateTime at) {
		return OpenStatusJudge.judge(detail, at, holidayCalendar.isHoliday(at.toLocalDate()));
	}

}
