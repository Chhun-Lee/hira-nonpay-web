package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.collector.config.CollectorProperties;
import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.hira.client.HiraCallGate;
import dev.chhun.hospitalcompare.hira.client.HiraClient;
import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.HospBasisPage;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.hospital.repository.HospitalUpsertRepository;
import dev.chhun.hospitalcompare.snapshot.repository.QualityIssueRepository;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 병원정보서비스 목록을 받아 새 STAGE 스냅샷에 적재하고, 검증 게이트를 통과하면 화면용(ACTIVE)으로 바꾼다.
 * 1페이지로 전체 건수를 본 뒤 나머지 페이지는 가상 스레드로 병렬 처리한다(동시 실행 수·초당 호출은 HiraCallGate가 제한).
 * 실패하면 STAGE를 FAILED로 남기고 이전 ACTIVE는 그대로 둔다. 다시 돌리면 처음부터 새로 받는다.
 */
@Service
@Profile("collector")
public class HospitalCollector {

	private static final Logger log = LoggerFactory.getLogger(HospitalCollector.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final HiraClient hiraClient;
	private final HospitalUpsertRepository hospitalUpsertRepository;
	private final QualityIssueRepository qualityIssueRepository;
	private final SnapshotService snapshotService;
	private final HiraProperties hiraProperties;
	private final int numOfRows;
	private final SnapshotGate gate;
	private final HospitalRecordValidator validator = new HospitalRecordValidator();

	public HospitalCollector(HiraClient hiraClient, HospitalUpsertRepository hospitalUpsertRepository,
			QualityIssueRepository qualityIssueRepository, SnapshotService snapshotService,
			HiraProperties hiraProperties, CollectorProperties properties) {
		this.hiraClient = hiraClient;
		this.hospitalUpsertRepository = hospitalUpsertRepository;
		this.qualityIssueRepository = qualityIssueRepository;
		this.snapshotService = snapshotService;
		this.hiraProperties = hiraProperties;
		this.numOfRows = properties.numOfRows();
		this.gate = new SnapshotGate(properties.gate().minCountRatio(), properties.gate().maxMissingRequiredRatio());
	}

	/**
	 * @param sgguCd 시군구코드. null이면 전국
	 */
	public CollectResult collect(String sgguCd) {
		long started = System.nanoTime();
		// 측정 비교용으로 이번 실행의 설정을 남긴다.
		log.info("수집 시작 범위={} | 동시 실행 {}, 초당 호출 {}, numOfRows {}, 재시도 {}",
				sgguCd == null ? "전국" : sgguCd, hiraProperties.maxConcurrency(), hiraProperties.requestsPerSecond(),
				numOfRows, hiraProperties.retry());
		HiraCallGate.Stats statsBefore = hiraClient.callStats();
		Integer previousActive = snapshotService.activeRecordCount().orElse(null);
		Snapshot stage = snapshotService.startStage(LocalDate.now(KST));
		Run run = new Run(stage.getId(), sgguCd);

		String failure;
		int loaded = 0;
		try {
			run.collectAllPages();
			loaded = Math.toIntExact(hospitalUpsertRepository.countBySnapshot(stage.getId()));
			if (run.received.get() != run.totalCount) {
				log.warn("받은 건수 {}건이 전체 건수 {}건과 다릅니다(수집 중 원본 변동 가능)", run.received.get(), run.totalCount);
			}
			failure = gate.check(previousActive, loaded, run.received.get(), run.missingRequired.get()).orElse(null);
		} catch (RuntimeException e) {
			log.error("수집 중단: {}", e.getMessage());
			failure = "수집 중단: " + e.getMessage();
		}

		SnapshotStatus status;
		if (failure == null) {
			snapshotService.activate(stage.getId(), loaded);
			status = SnapshotStatus.ACTIVE;
		} else {
			snapshotService.fail(stage.getId(), failure);
			status = SnapshotStatus.FAILED;
		}
		int deleted = snapshotService.cleanup();
		HiraCallGate.Stats stats = hiraClient.callStats().minus(statsBefore);

		return new CollectResult(sgguCd == null ? "전국" : sgguCd, stage.getId(), status, failure,
				stats.calls(), stats.retries(), stats.perSecondLimited(),
				Duration.ofNanos(System.nanoTime() - started),
				Duration.ofNanos(stats.callNanos()), Duration.ofNanos(run.dbNanos.sum()),
				run.totalCount, run.received.get(), loaded, deleted,
				qualityIssueRepository.countBySnapshot(stage.getId()));
	}

	/** 수집 한 번의 페이지 처리와 집계 */
	private final class Run {

		private final long snapshotId;
		private final String sgguCd;
		private final AtomicInteger received = new AtomicInteger();
		private final AtomicInteger missingRequired = new AtomicInteger();
		private final LongAdder dbNanos = new LongAdder();
		private int totalCount;

		private Run(long snapshotId, String sgguCd) {
			this.snapshotId = snapshotId;
			this.sgguCd = sgguCd;
		}

		void collectAllPages() {
			HospBasisPage first = fetch(1, numOfRows);
			totalCount = first.totalCount();
			store(first);

			int pageSize = pageSize(first);
			int pages = (int) Math.ceil((double) totalCount / pageSize);
			if (pages <= 1) {
				return;
			}
			try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
				CompletionService<Void> completion = new ExecutorCompletionService<>(executor);
				List<Future<Void>> futures = new ArrayList<>();
				for (int pageNo = 2; pageNo <= pages; pageNo++) {
					int page = pageNo;
					futures.add(completion.submit(() -> {
						store(fetch(page, pageSize));
						return null;
					}));
				}
				awaitAll(completion, futures);
			}
		}

		/** 하나라도 실패하면 남은 페이지를 취소하고 그 오류를 던진다. */
		private void awaitAll(CompletionService<Void> completion, List<Future<Void>> futures) {
			try {
				for (int done = 0; done < futures.size(); done++) {
					completion.take().get();
				}
			} catch (ExecutionException e) {
				futures.forEach(future -> future.cancel(true));
				throw e.getCause() instanceof RuntimeException runtime
						? runtime
						: new IllegalStateException(e.getCause());
			} catch (InterruptedException e) {
				futures.forEach(future -> future.cancel(true));
				Thread.currentThread().interrupt();
				throw new IllegalStateException("수집이 중단되었습니다");
			}
		}

		/** API가 요청보다 작은 페이지를 주면(허용 최대값을 모른다) 그 크기로 나머지를 요청한다. */
		private int pageSize(HospBasisPage first) {
			int got = first.items().size();
			if (got > 0 && got < numOfRows && got < first.totalCount()) {
				log.info("1페이지가 {}건만 와서 페이지 크기를 {}건으로 줄여 요청합니다(요청 {}건)", got, got, numOfRows);
				return got;
			}
			return numOfRows;
		}

		private HospBasisPage fetch(int pageNo, int size) {
			return hiraClient.getHospBasisList(sgguCd, pageNo, size);
		}

		private void store(HospBasisPage page) {
			HospitalRecordValidator.Result result = validator.validate(page.items());
			long started = System.nanoTime();
			hospitalUpsertRepository.upsertAll(snapshotId, result.records());
			qualityIssueRepository.saveAll(snapshotId, result.issues());
			dbNanos.add(System.nanoTime() - started);
			received.addAndGet(page.items().size());
			missingRequired.addAndGet(result.missingRequired());
		}

	}

}
