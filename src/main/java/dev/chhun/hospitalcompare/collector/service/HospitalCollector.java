package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.hira.client.HiraClient;
import dev.chhun.hospitalcompare.hira.dto.HospBasisItem;
import dev.chhun.hospitalcompare.hira.dto.HospBasisPage;
import dev.chhun.hospitalcompare.hospital.dto.HospitalRecord;
import dev.chhun.hospitalcompare.hospital.entity.Snapshot;
import dev.chhun.hospitalcompare.hospital.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.hospital.repository.HospitalUpsertRepository;
import dev.chhun.hospitalcompare.hospital.repository.SnapshotRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 병원정보서비스 목록을 시군구 단위로 받아 active 스냅샷에 upsert한다.
 * 배치 프레임워크 없이 한 번에 돌고, 중간에 실패하면 처음부터 다시 돌린다(upsert라 재실행해도 건수가 같다).
 */
@Service
@Profile("collector")
public class HospitalCollector {

	private static final Logger log = LoggerFactory.getLogger(HospitalCollector.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final HiraClient hiraClient;
	private final HospitalUpsertRepository hospitalUpsertRepository;
	private final SnapshotRepository snapshotRepository;
	private final int numOfRows;

	public HospitalCollector(HiraClient hiraClient, HospitalUpsertRepository hospitalUpsertRepository,
			SnapshotRepository snapshotRepository, @Value("${collector.num-of-rows}") int numOfRows) {
		this.hiraClient = hiraClient;
		this.hospitalUpsertRepository = hospitalUpsertRepository;
		this.snapshotRepository = snapshotRepository;
		this.numOfRows = numOfRows;
	}

	public CollectResult collect(String sgguCd) {
		long started = System.nanoTime();
		Snapshot snapshot = activeSnapshot();

		int calls = 0;
		int received = 0;
		long writeNanos = 0;
		for (int pageNo = 1; ; pageNo++) {
			HospBasisPage page = hiraClient.getHospBasisList(sgguCd, pageNo, numOfRows);
			calls++;
			received += page.items().size();

			long writeStarted = System.nanoTime();
			hospitalUpsertRepository.upsertAll(snapshot.getId(), toRecords(page.items()));
			writeNanos += System.nanoTime() - writeStarted;

			// 요청한 numOfRows보다 적게 줄 수 있으므로 페이지 수를 계산하지 않고 받은 건수로 끝을 판단한다.
			if (page.items().isEmpty() || received >= page.totalCount()) {
				break;
			}
		}

		long recordCount = hospitalUpsertRepository.countBySnapshot(snapshot.getId());
		snapshot.markCollected(LocalDate.now(KST), Math.toIntExact(recordCount));
		snapshotRepository.save(snapshot);

		return new CollectResult(sgguCd, calls, received, snapshot.getId(), recordCount,
				Duration.ofNanos(System.nanoTime() - started), Duration.ofNanos(writeNanos));
	}

	/** 2단계는 active 스냅샷 하나에 계속 덮어쓴다. stage → active 전환은 3단계에서 한다. */
	private Snapshot activeSnapshot() {
		return snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)
				.orElseGet(() -> snapshotRepository.save(new Snapshot(LocalDate.now(KST), SnapshotStatus.ACTIVE)));
	}

	private static List<HospitalRecord> toRecords(List<HospBasisItem> items) {
		return items.stream()
				.map(item -> new HospitalRecord(
						item.ykiho(),
						item.yadmNm(),
						item.clCd(),
						item.clCdNm(),
						item.sidoCd(),
						item.sidoCdNm(),
						item.sgguCd(),
						item.sgguCdNm(),
						item.emdongNm(),
						item.addr(),
						item.telno(),
						parseDate(item.estbDd()),
						item.yPos(),
						item.xPos(),
						item.drTotCnt()))
				.toList();
	}

	private static LocalDate parseDate(String yyyyMMdd) {
		if (yyyyMMdd == null || yyyyMMdd.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(yyyyMMdd, DateTimeFormatter.BASIC_ISO_DATE);
		} catch (DateTimeParseException e) {
			log.warn("개설일자 형식이 아니어서 비워 둡니다: {}", yyyyMMdd);
			return null;
		}
	}

}
