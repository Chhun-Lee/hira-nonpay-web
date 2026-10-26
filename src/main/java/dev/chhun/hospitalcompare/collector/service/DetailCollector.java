package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.collector.config.CollectorProperties;
import dev.chhun.hospitalcompare.collector.dto.DetailCollectResult;
import dev.chhun.hospitalcompare.detail.config.HospitalLevelProperties;
import dev.chhun.hospitalcompare.detail.repository.DetailUpsertRepository;
import dev.chhun.hospitalcompare.hira.client.DetailClient;
import dev.chhun.hospitalcompare.hira.client.HiraCallGate;
import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.DetailPage;
import dev.chhun.hospitalcompare.hira.dto.DtlInfoItem;
import dev.chhun.hospitalcompare.hira.dto.SubjectItem;
import dev.chhun.hospitalcompare.hira.exception.HiraException;
import dev.chhun.hospitalcompare.hospital.dto.HospitalRef;
import dev.chhun.hospitalcompare.hospital.repository.HospitalQueryRepository;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.repository.QualityIssueRepository;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 병원급 기관마다 세부정보·진료과목을 받아 새 STAGE 스냅샷에 적재하고, 검증 게이트를 통과하면 ACTIVE로 바꾼다.
 * 대상은 병원 목록 ACTIVE의 병원급(hospital.level-cl-codes)이다. 두 오퍼레이션을 기관마다 따로 병렬로 부르고,
 * 하나라도 끝내 실패하면 나머지를 취소하고 FAILED로 끝낸다(일부 기관이 빠진 상세는 게시하지 않는다).
 */
@Service
@Profile("collector")
public class DetailCollector {

	private static final Logger log = LoggerFactory.getLogger(DetailCollector.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final DetailClient detailClient;
	private final HospitalQueryRepository hospitalQueryRepository;
	private final DetailUpsertRepository upsertRepository;
	private final QualityIssueRepository qualityIssueRepository;
	private final SnapshotService snapshotService;
	private final HospitalLevelProperties hospitalLevel;
	private final HiraProperties.Detail service;
	private final CollectorProperties.Detail settings;
	private final DetailGate gate;
	private final DetailRecordValidator validator = new DetailRecordValidator();

	public DetailCollector(DetailClient detailClient, HospitalQueryRepository hospitalQueryRepository,
			DetailUpsertRepository upsertRepository, QualityIssueRepository qualityIssueRepository,
			SnapshotService snapshotService, HospitalLevelProperties hospitalLevel, HiraProperties hiraProperties,
			CollectorProperties properties) {
		this.detailClient = detailClient;
		this.hospitalQueryRepository = hospitalQueryRepository;
		this.upsertRepository = upsertRepository;
		this.qualityIssueRepository = qualityIssueRepository;
		this.snapshotService = snapshotService;
		this.hospitalLevel = hospitalLevel;
		this.service = hiraProperties.detail();
		this.settings = properties.detail();
		CollectorProperties.DetailGateSettings gateSettings = settings.gate();
		this.gate = new DetailGate(gateSettings.maxEmptySubjectRatio(), gateSettings.minCountRatio(),
				gateSettings.maxInvalidHoursRatio());
	}

	/**
	 * @param sgguCd 시험 실행할 시군구. null이면 전국 병원급. 시험 실행은 ACTIVE가 되지 않는다.
	 * @throws IllegalStateException 병원 목록 ACTIVE가 없을 때(대상을 정할 수 없다)
	 */
	public DetailCollectResult collect(String sgguCd) {
		long started = System.nanoTime();
		boolean trial = sgguCd != null;
		long hospitalSnapshotId = snapshotService.activeSnapshotId(SnapshotSource.HOSPITAL_LIST)
				.orElseThrow(() -> new IllegalStateException("병원 목록 ACTIVE가 없어 상세정보 수집 대상을 정할 수 없습니다"));
		List<HospitalRef> targets = hospitalQueryRepository.findDetailTargets(hospitalSnapshotId,
				hospitalLevel.levelClCodes(), sgguCd);
		log.info("상세정보 수집 시작{} | 대상 병원급 {}곳 | 동시 실행 {}, 초당 호출 {}, 읽기 제한 {}, numOfRows {}",
				trial ? "(시험 실행 시군구 " + sgguCd + ")" : "", targets.size(), service.maxConcurrency(),
				service.requestsPerSecond(), service.readTimeout(), settings.numOfRows());
		HiraCallGate.Stats statsBefore = detailClient.callStats();
		Integer previousActive = snapshotService.activeRecordCount(SnapshotSource.HOSPITAL_DETAIL).orElse(null);
		Snapshot stage = snapshotService.startStage(SnapshotSource.HOSPITAL_DETAIL, LocalDate.now(KST));
		Run run = new Run(stage.getId());

		String failure;
		boolean aborted = false;
		try {
			run.collect(targets);
			// 시험 실행은 일부 시군구만 받으므로 이전 ACTIVE 기관 수와 견주지 않는다.
			failure = gate.check(trial ? null : previousActive, targets.size(), run.withoutSubjects(targets.size()),
					run.detailRows.get(), run.hourFields.get(), run.invalidHourFields.get()).orElse(null);
		} catch (RuntimeException e) {
			if (e instanceof HiraException) {
				log.error("상세정보 수집 중단: {}", e.getMessage());
			} else {
				log.error("상세정보 수집 중단: {}", e.getMessage(), e);
			}
			failure = "수집 중단: " + e.getMessage();
			aborted = true;
		}

		SnapshotStatus status;
		String reason;
		if (trial && !aborted) {
			reason = "시험 실행(시군구 " + sgguCd + ", " + targets.size() + "곳)"
					+ (failure == null ? "" : " / 게이트: " + failure);
			snapshotService.endTrial(stage.getId(), reason);
			status = SnapshotStatus.TRIAL;
		} else if (failure == null) {
			reason = null;
			snapshotService.activate(stage.getId(), run.detailRows.get());
			status = SnapshotStatus.ACTIVE;
		} else {
			reason = failure;
			snapshotService.fail(stage.getId(), failure);
			status = SnapshotStatus.FAILED;
		}
		// 이하는 정보용이다. 상태는 이미 정해졌으므로 실패해도 수집 결과를 바꾸지 않는다.
		int deleted = 0;
		try {
			deleted = snapshotService.cleanup(SnapshotSource.HOSPITAL_DETAIL);
		} catch (RuntimeException e) {
			log.warn("상세정보 정리에 실패했습니다(다음 실행에서 다시 정리합니다): {}", e.getMessage());
		}
		HiraCallGate.Stats stats = detailClient.callStats().minus(statsBefore);

		return new DetailCollectResult(stage.getId(), status, reason, trial, trial ? sgguCd : "전국",
				targets.size(), stats.calls(), stats.retries(), stats.perSecondLimited(),
				Duration.ofNanos(System.nanoTime() - started), Duration.ofNanos(stats.callNanos()),
				Duration.ofNanos(run.dbNanos.sum()), run.detailRows.get(), run.emptyDetails.get(),
				run.subjectRows.get(), run.withoutSubjects(targets.size()), run.hourFields.get(),
				run.invalidHourFields.get(), run.coverage(targets), deleted, issueCounts(stage.getId()));
	}

	private Map<QualityIssueType, Integer> issueCounts(long snapshotId) {
		try {
			return qualityIssueRepository.countBySnapshot(snapshotId);
		} catch (RuntimeException e) {
			log.warn("품질 이슈 집계에 실패했습니다: {}", e.getMessage());
			return Map.of();
		}
	}

	/** 수집 한 번의 호출과 집계 */
	private final class Run {

		private final long snapshotId;
		private final Set<String> withSubjects = ConcurrentHashMap.newKeySet();
		private final Map<String, AtomicInteger> detailByClCd = new ConcurrentHashMap<>();
		private final AtomicInteger detailRows = new AtomicInteger();
		private final AtomicInteger emptyDetails = new AtomicInteger();
		private final AtomicInteger subjectRows = new AtomicInteger();
		private final AtomicInteger hourFields = new AtomicInteger();
		private final AtomicInteger invalidHourFields = new AtomicInteger();
		private final LongAdder dbNanos = new LongAdder();
		private final Progress progress = new Progress();

		private Run(long snapshotId) {
			this.snapshotId = snapshotId;
		}

		/** 기관마다 세부정보·진료과목을 따로 작업으로 만들어 동시에 돌린다. 동시 실행 수와 초당 호출은 관문이 제한한다. */
		void collect(List<HospitalRef> targets) {
			List<Callable<Void>> tasks = new ArrayList<>(targets.size() * 2);
			for (HospitalRef target : targets) {
				tasks.add(() -> {
					storeDetail(target, allPages((pageNo, rows) -> detailClient.getDetail(target.ykiho(), pageNo, rows)));
					progress.done();
					return null;
				});
				tasks.add(() -> {
					storeSubjects(target,
							allPages((pageNo, rows) -> detailClient.getSubjects(target.ykiho(), pageNo, rows)));
					progress.done();
					return null;
				});
			}
			progress.start(tasks.size());
			FailFastTasks.runAll(tasks, service.maxConcurrency());
		}

		int withoutSubjects(int targets) {
			return targets - withSubjects.size();
		}

		Map<String, DetailCollectResult.Coverage> coverage(List<HospitalRef> targets) {
			Map<String, Integer> byClCd = new TreeMap<>();
			targets.forEach(target -> byClCd.merge(target.clCd(), 1, Integer::sum));
			Map<String, DetailCollectResult.Coverage> coverage = new TreeMap<>();
			byClCd.forEach((clCd, count) -> coverage.put(clCd, new DetailCollectResult.Coverage(count,
					detailByClCd.getOrDefault(clCd, new AtomicInteger()).get())));
			return coverage;
		}

		private void storeDetail(HospitalRef target, List<DtlInfoItem> items) {
			if (items.isEmpty()) {
				emptyDetails.incrementAndGet();
				return;
			}
			if (items.size() > 1) {
				log.warn("세부정보가 {}건 왔습니다. 첫 건만 씁니다: {}", items.size(), target.ykiho());
			}
			DetailRecordValidator.DetailResult result = validator.validateDetail(target.ykiho(), items.getFirst());
			timed(() -> {
				upsertRepository.upsertDetails(snapshotId, List.of(result.record()));
				qualityIssueRepository.saveAll(snapshotId, result.issues());
			});
			detailRows.incrementAndGet();
			hourFields.addAndGet(result.hourFields());
			invalidHourFields.addAndGet(result.invalidHourFields());
			detailByClCd.computeIfAbsent(target.clCd(), key -> new AtomicInteger()).incrementAndGet();
		}

		private void storeSubjects(HospitalRef target, List<SubjectItem> items) {
			DetailRecordValidator.SubjectResult result = validator.validateSubjects(target.ykiho(), items);
			timed(() -> {
				upsertRepository.upsertSubjects(snapshotId, result.records());
				qualityIssueRepository.saveAll(snapshotId, result.issues());
			});
			subjectRows.addAndGet(result.records().size());
			if (!result.records().isEmpty()) {
				withSubjects.add(target.ykiho());
			}
		}

		/** 기관 하나의 응답을 끝까지 받는다. 실측으로는 한 페이지에 다 왔다(최대 35행). */
		private <T> List<T> allPages(BiFunction<Integer, Integer, DetailPage<T>> fetch) {
			DetailPage<T> page = fetch.apply(1, settings.numOfRows());
			List<T> items = new ArrayList<>(page.items());
			for (int pageNo = 2; items.size() < page.totalCount() && !page.items().isEmpty(); pageNo++) {
				page = fetch.apply(pageNo, settings.numOfRows());
				items.addAll(page.items());
			}
			return items;
		}

		private void timed(Runnable store) {
			long started = System.nanoTime();
			store.run();
			dbNanos.add(System.nanoTime() - started);
		}

	}

	/** 진행 상황을 주기적으로 남긴다. 별도 스레드 없이, 작업이 끝날 때 간격이 지났으면 남긴다. */
	private final class Progress {

		private final long startedNanos = System.nanoTime();
		private final AtomicLong lastLogNanos = new AtomicLong(System.nanoTime());
		private final AtomicInteger done = new AtomicInteger();
		private volatile int total;

		void start(int total) {
			this.total = total;
			done.set(0);
		}

		void done() {
			int finished = done.incrementAndGet();
			long now = System.nanoTime();
			long last = lastLogNanos.get();
			if (now - last >= settings.progressInterval().toNanos() && lastLogNanos.compareAndSet(last, now)) {
				HiraCallGate.Stats stats = detailClient.callStats();
				log.info("진행 상세정보 {}/{}, 경과 {}분, 누적 호출 {}, 재시도 {}", finished, total,
						Duration.ofNanos(now - startedNanos).toMinutes(), stats.calls(), stats.retries());
			}
		}

	}

}
