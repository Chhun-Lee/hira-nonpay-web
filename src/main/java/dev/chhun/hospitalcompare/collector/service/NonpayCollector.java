package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.collector.config.CollectorProperties;
import dev.chhun.hospitalcompare.collector.dto.NonpayCollectResult;
import dev.chhun.hospitalcompare.hira.client.HiraCallGate;
import dev.chhun.hospitalcompare.hira.client.NonpayClient;
import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.NonpayHospPrice;
import dev.chhun.hospitalcompare.hira.dto.NonpayItemCode;
import dev.chhun.hospitalcompare.hira.dto.NonpayPage;
import dev.chhun.hospitalcompare.nonpay.entity.StatDimension;
import dev.chhun.hospitalcompare.nonpay.repository.NonpayItemRepository;
import dev.chhun.hospitalcompare.nonpay.repository.NonpayPriceRepository;
import dev.chhun.hospitalcompare.nonpay.repository.NonpayStatRepository;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.repository.QualityIssueRepository;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 비급여 가격을 항목 중심으로 받아 새 STAGE 스냅샷에 적재하고, 검증 게이트를 통과하면 ACTIVE로 바꾼다.
 * 항목코드 → 종별·시도별 통계(통계에 나온 코드가 대상 항목) → 항목별 병원 목록 순서다.
 * 병원 목록은 1차로 대상 항목의 1페이지를 병렬로 받아 항목별 전체 건수를 알고, 2차로 나머지 페이지를 병렬로 받는다.
 * 하나라도 끝내 실패하면 나머지를 취소하고 FAILED로 끝낸다(일부 항목이 빠진 비교 데이터는 게시하지 않는다).
 */
@Service
@Profile("collector")
public class NonpayCollector {

	private static final Logger log = LoggerFactory.getLogger(NonpayCollector.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final NonpayClient nonpayClient;
	private final NonpayItemRepository itemRepository;
	private final NonpayPriceRepository priceRepository;
	private final NonpayStatRepository statRepository;
	private final QualityIssueRepository qualityIssueRepository;
	private final SnapshotService snapshotService;
	private final HiraProperties.Nonpay service;
	private final CollectorProperties.Nonpay settings;
	private final NonpayGate gate;
	private final NonpayItemValidator itemValidator = new NonpayItemValidator();
	private final NonpayPriceValidator priceValidator = new NonpayPriceValidator();
	private final NonpayStatFlattener statFlattener = new NonpayStatFlattener();

	public NonpayCollector(NonpayClient nonpayClient, NonpayItemRepository itemRepository,
			NonpayPriceRepository priceRepository, NonpayStatRepository statRepository,
			QualityIssueRepository qualityIssueRepository, SnapshotService snapshotService,
			HiraProperties hiraProperties, CollectorProperties properties) {
		this.nonpayClient = nonpayClient;
		this.itemRepository = itemRepository;
		this.priceRepository = priceRepository;
		this.statRepository = statRepository;
		this.qualityIssueRepository = qualityIssueRepository;
		this.snapshotService = snapshotService;
		this.service = hiraProperties.nonpay();
		this.settings = properties.nonpay();
		CollectorProperties.NonpayGateSettings gateSettings = settings.gate();
		this.gate = new NonpayGate(gateSettings.minCountRatio(), gateSettings.maxInvalidRatio(),
				gateSettings.maxEmptyItemRatio());
	}

	/**
	 * @param trialItems 시험 실행할 항목 코드. 비어 있으면 통계에 나온 전 항목을 받는다. 시험 실행은 ACTIVE가 되지 않는다.
	 */
	public NonpayCollectResult collect(List<String> trialItems) {
		long started = System.nanoTime();
		boolean trial = !trialItems.isEmpty();
		log.info("비급여 수집 시작{} | 동시 실행 {}, 초당 호출 {}, 읽기 제한 {}, numOfRows {}",
				trial ? "(시험 실행 " + trialItems + ")" : "", service.maxConcurrency(), service.requestsPerSecond(),
				service.readTimeout(), settings.numOfRows());
		HiraCallGate.Stats statsBefore = nonpayClient.callStats();
		Integer previousActive = snapshotService.activeRecordCount(SnapshotSource.NONPAY).orElse(null);
		Snapshot stage = snapshotService.startStage(SnapshotSource.NONPAY, LocalDate.now(KST));
		Run run = new Run(stage.getId());

		String failure;
		boolean aborted = false;
		int loaded = 0;
		try {
			run.collectItemCodes();
			run.collectStats();
			List<String> targets = run.targets(trialItems);
			log.info("비급여 대상 항목 {}개", targets.size());
			run.collectPrices(targets);
			loaded = Math.toIntExact(priceRepository.countBySnapshot(stage.getId()));
			failure = gate.check(previousActive, loaded, run.received.get(), run.excluded.get(), run.targetItems,
					run.emptyItems()).orElse(null);
		} catch (RuntimeException e) {
			log.error("비급여 수집 중단: {}", e.getMessage());
			failure = "수집 중단: " + e.getMessage();
			aborted = true;
		}
		// 정리가 실패·시험 실행 스냅샷의 행을 지우므로 그 전에 센다.
		Long unmatched = snapshotService.activeSnapshotId(SnapshotSource.HOSPITAL_LIST)
				.map(hospitalSnapshotId -> priceRepository.countUnmatchedYkiho(stage.getId(), hospitalSnapshotId))
				.orElse(null);

		SnapshotStatus status;
		String reason;
		if (trial && !aborted) {
			reason = "시험 실행(일부 항목 " + trialItems.size() + "개)" + (failure == null ? "" : " / 게이트: " + failure);
			snapshotService.endTrial(stage.getId(), reason);
			status = SnapshotStatus.TRIAL;
		} else if (failure == null) {
			reason = null;
			snapshotService.activate(stage.getId(), loaded);
			status = SnapshotStatus.ACTIVE;
		} else {
			reason = failure;
			snapshotService.fail(stage.getId(), failure);
			status = SnapshotStatus.FAILED;
		}
		int deleted = snapshotService.cleanup(SnapshotSource.NONPAY);
		HiraCallGate.Stats stats = nonpayClient.callStats().minus(statsBefore);

		return new NonpayCollectResult(stage.getId(), status, reason, trial, run.targetItems,
				stats.calls(), stats.retries(), stats.perSecondLimited(),
				Duration.ofNanos(System.nanoTime() - started), Duration.ofNanos(stats.callNanos()),
				Duration.ofNanos(run.dbNanos.sum()), run.itemCodes, run.statRows,
				run.received.get(), loaded, run.excluded.get(), run.emptyItems(), unmatched, deleted,
				qualityIssueRepository.countBySnapshot(stage.getId()));
	}

	/** 수집 한 번의 호출 순서와 집계 */
	private final class Run {

		private final long snapshotId;
		private final Set<String> itemCodeSet = new HashSet<>();
		private final Set<String> statItems = new LinkedHashSet<>();
		private final Set<String> itemsWithPrice = ConcurrentHashMap.newKeySet();
		private final Map<String, AtomicInteger> receivedByItem = new ConcurrentHashMap<>();
		private final Map<String, Integer> totalByItem = new ConcurrentHashMap<>();
		private final AtomicInteger received = new AtomicInteger();
		private final AtomicInteger excluded = new AtomicInteger();
		private final LongAdder dbNanos = new LongAdder();
		private final Progress progress = new Progress();
		private int itemCodes;
		private int statRows;
		private int targetItems;

		private Run(long snapshotId) {
			this.snapshotId = snapshotId;
		}

		void collectItemCodes() {
			sequentialPages(nonpayClient::getItemCodes, page -> {
				NonpayItemValidator.Result result = itemValidator.validate(page.items());
				timed(() -> {
					itemRepository.upsertAll(snapshotId, result.records());
					qualityIssueRepository.saveAll(snapshotId, result.issues());
				});
				itemCodes += result.records().size();
				result.records().forEach(record -> itemCodeSet.add(record.npayCd()));
			});
		}

		void collectStats() {
			sequentialPages(nonpayClient::getTypeStats, page -> storeStats(StatDimension.CL_TYPE, page));
			sequentialPages(nonpayClient::getSidoStats, page -> storeStats(StatDimension.SIDO, page));
		}

		/** 대상 항목 = 종별 통계에 나온 코드. 시험 실행이면 지정한 코드(통계에 없으면 실행하지 않는다) */
		List<String> targets(List<String> trialItems) {
			long notInCodes = statItems.stream().filter(code -> !itemCodeSet.contains(code)).count();
			if (notInCodes > 0) {
				log.warn("통계에는 있고 항목 코드 목록에는 없는 코드가 {}개 있습니다", notInCodes);
			}
			List<String> targets;
			if (trialItems.isEmpty()) {
				targets = List.copyOf(statItems);
			} else {
				List<String> unknown = trialItems.stream().filter(code -> !statItems.contains(code)).toList();
				if (!unknown.isEmpty()) {
					throw new IllegalArgumentException("통계에 없는 항목 코드라 시험 실행할 수 없습니다: " + unknown);
				}
				targets = List.copyOf(trialItems);
			}
			targetItems = targets.size();
			return targets;
		}

		void collectPrices(List<String> targets) {
			progress.start("1차(항목별 1페이지)", targets.size());
			Map<String, Integer> pageSizes = new ConcurrentHashMap<>();
			List<Callable<Void>> firstPages = new ArrayList<>();
			for (String item : targets) {
				firstPages.add(() -> {
					NonpayPage<NonpayHospPrice> first = nonpayClient.getHospPrices(item, 1, settings.numOfRows());
					storePrices(item, first);
					totalByItem.put(item, first.totalCount());
					pageSizes.put(item, pageSize(first));
					progress.done();
					return null;
				});
			}
			FailFastTasks.runAll(firstPages);

			List<Callable<Void>> restPages = new ArrayList<>();
			for (String item : targets) {
				int size = pageSizes.get(item);
				int pages = pageCount(totalByItem.get(item), size);
				for (int pageNo = 2; pageNo <= pages; pageNo++) {
					int page = pageNo;
					restPages.add(() -> {
						storePrices(item, nonpayClient.getHospPrices(item, page, size));
						progress.done();
						return null;
					});
				}
			}
			progress.start("2차(나머지 페이지)", restPages.size());
			FailFastTasks.runAll(restPages);

			long mismatched = targets.stream()
					.filter(item -> receivedByItem.getOrDefault(item, new AtomicInteger()).get() != totalByItem.get(item))
					.count();
			if (mismatched > 0) {
				log.warn("받은 건수가 전체 건수와 다른 항목이 {}개 있습니다(수집 중 원본 변동 가능)", mismatched);
			}
		}

		int emptyItems() {
			return targetItems - itemsWithPrice.size();
		}

		private void storeStats(StatDimension dimension, NonpayPage<Map<String, String>> page) {
			NonpayStatFlattener.Result result = statFlattener.flatten(dimension, page.items());
			timed(() -> {
				statRepository.upsertAll(snapshotId, result.records());
				qualityIssueRepository.saveAll(snapshotId, result.issues());
			});
			statRows += result.records().size();
			if (dimension == StatDimension.CL_TYPE) {
				statItems.addAll(result.npayCds());
			}
		}

		private void storePrices(String item, NonpayPage<NonpayHospPrice> page) {
			NonpayPriceValidator.Result result = priceValidator.validate(item, page.items());
			timed(() -> {
				priceRepository.upsertAll(snapshotId, result.records());
				qualityIssueRepository.saveAll(snapshotId, result.issues());
			});
			received.addAndGet(page.items().size());
			excluded.addAndGet(result.excluded());
			receivedByItem.computeIfAbsent(item, key -> new AtomicInteger()).addAndGet(page.items().size());
			if (!result.records().isEmpty()) {
				itemsWithPrice.add(item);
			}
		}

		/** 항목코드·통계처럼 1~2페이지짜리 목록을 차례로 끝까지 받는다. */
		private <T> void sequentialPages(BiFunction<Integer, Integer, NonpayPage<T>> fetch,
				Consumer<NonpayPage<T>> store) {
			NonpayPage<T> first = fetch.apply(1, settings.numOfRows());
			store.accept(first);
			int size = pageSize(first);
			int pages = pageCount(first.totalCount(), size);
			for (int pageNo = 2; pageNo <= pages; pageNo++) {
				store.accept(fetch.apply(pageNo, size));
			}
		}

		/** API가 요청보다 작은 페이지를 주면(허용 최대값을 모른다) 그 크기로 나머지를 요청한다. */
		private int pageSize(NonpayPage<?> first) {
			int got = first.items().size();
			if (got > 0 && got < settings.numOfRows() && got < first.totalCount()) {
				log.info("1페이지가 {}건만 와서 페이지 크기를 {}건으로 줄여 요청합니다(요청 {}건)", got, got,
						settings.numOfRows());
				return got;
			}
			return settings.numOfRows();
		}

		private static int pageCount(int totalCount, int pageSize) {
			return Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
		}

		private void timed(Runnable store) {
			long started = System.nanoTime();
			store.run();
			dbNanos.add(System.nanoTime() - started);
		}

	}

	/** 몇 시간짜리 실행이라 진행 상황을 주기적으로 남긴다. 별도 스레드 없이, 작업이 끝날 때 간격이 지났으면 남긴다. */
	private final class Progress {

		private final long startedNanos = System.nanoTime();
		private final AtomicLong lastLogNanos = new AtomicLong(System.nanoTime());
		private final AtomicInteger done = new AtomicInteger();
		private volatile String phase;
		private volatile int total;

		void start(String phase, int total) {
			this.phase = phase;
			this.total = total;
			done.set(0);
		}

		void done() {
			int finished = done.incrementAndGet();
			long now = System.nanoTime();
			long last = lastLogNanos.get();
			if (now - last >= settings.progressInterval().toNanos() && lastLogNanos.compareAndSet(last, now)) {
				HiraCallGate.Stats stats = nonpayClient.callStats();
				log.info("진행 {} {}/{}, 경과 {}분, 누적 호출 {}, 재시도 {}", phase, finished, total,
						Duration.ofNanos(now - startedNanos).toMinutes(), stats.calls(), stats.retries());
			}
		}

	}

}
