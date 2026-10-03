package dev.chhun.hospitalcompare.collector.runner;

import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.collector.dto.NonpayCollectResult;
import dev.chhun.hospitalcompare.collector.service.HospitalCollector;
import dev.chhun.hospitalcompare.collector.service.NonpayCollector;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * collector 프로파일 진입점. 수집이 끝나면 프로세스가 종료되고, 실패하면 예외로 0이 아닌 종료 코드를 남긴다.
 * <ul>
 * <li>인자 없음 또는 {@code --target=hospital}: 병원 목록 전국. {@code --sgguCd=110001}이면 그 시군구만(개발용)</li>
 * <li>{@code --target=nonpay}: 비급여 전 항목. {@code --items=ABZ010001,HE1180000}이면 그 항목만 시험 실행</li>
 * </ul>
 * 점(.)이 없는 옵션은 위 셋만 받는다. 오타 하나로 몇 시간짜리 전국 수집이 도는 것을 막는다.
 * 점이 있는 옵션(--hira.nonpay.max-concurrency=8 등)은 Spring 설정이다.
 */
@Component
@Profile("collector")
@ConditionalOnProperty(name = "collector.run-on-startup", havingValue = "true", matchIfMissing = true)
class CollectorRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

	private static final Set<String> OPTIONS = Set.of("target", "sgguCd", "items");

	private final HospitalCollector hospitalCollector;
	private final NonpayCollector nonpayCollector;

	CollectorRunner(HospitalCollector hospitalCollector, NonpayCollector nonpayCollector) {
		this.hospitalCollector = hospitalCollector;
		this.nonpayCollector = nonpayCollector;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!args.getNonOptionArgs().isEmpty()) {
			throw new IllegalArgumentException("인자는 --이름=값 형식으로 넘겨야 합니다: " + args.getNonOptionArgs());
		}
		List<String> unknown = args.getOptionNames().stream()
				.filter(name -> !name.contains("."))
				.filter(name -> !OPTIONS.contains(name))
				.sorted()
				.toList();
		if (!unknown.isEmpty()) {
			throw new IllegalArgumentException("알 수 없는 옵션입니다: " + unknown + " (target, sgguCd, items만 받습니다)");
		}
		String target = single(args, "target");
		if (target == null || target.equals("hospital")) {
			runHospital(args);
		} else if (target.equals("nonpay")) {
			runNonpay(args);
		} else {
			throw new IllegalArgumentException("--target은 hospital 또는 nonpay여야 합니다: " + target);
		}
	}

	private void runHospital(ApplicationArguments args) {
		if (args.containsOption("items")) {
			throw new IllegalArgumentException("--items는 --target=nonpay에서만 씁니다");
		}
		CollectResult result = hospitalCollector.collect(single(args, "sgguCd"));
		log.info("수집 {} 범위={} 스냅샷={} | 호출 {}회(재시도 {}, 초당 한도 {}) | 소요 {}ms(API 응답 합 {}ms, DB 합 {}ms)"
						+ " | 전체 {}건, 수신 {}건, 적재 {}건, 정리 {}행 | 품질 이슈 {}",
				result.succeeded() ? "완료" : "실패", result.scope(), result.snapshotId(),
				result.calls(), result.retries(), result.perSecondLimited(),
				result.elapsed().toMillis(), result.apiElapsed().toMillis(), result.dbElapsed().toMillis(),
				result.totalCount(), result.received(), result.loaded(), result.deleted(), result.issues());
		if (!result.succeeded()) {
			throw new IllegalStateException("수집 실패: " + result.failureReason());
		}
	}

	private void runNonpay(ApplicationArguments args) {
		if (args.containsOption("sgguCd")) {
			throw new IllegalArgumentException("--sgguCd는 병원 목록 수집(--target=hospital)에서만 씁니다");
		}
		List<String> items = List.of();
		if (args.containsOption("items")) {
			items = Arrays.stream(single(args, "items").split(","))
					.map(String::trim)
					.filter(code -> !code.isEmpty())
					.distinct()
					.toList();
			if (items.isEmpty()) {
				throw new IllegalArgumentException("--items에 항목 코드가 없습니다. 예: --items=ABZ010001,HE1180000");
			}
		}
		NonpayCollectResult result = nonpayCollector.collect(items);
		log.info("비급여 수집 {} 스냅샷={} | 대상 항목 {} | 호출 {}회(재시도 {}, 초당 한도 {})"
						+ " | 소요 {}ms(API 응답 합 {}ms, DB 합 {}ms) | 항목 코드 {}, 통계 {}행"
						+ " | 가격 수신 {}행, 적재 {}행, 제외 {}행, 가격 없는 항목 {} | 병원 목록에 없는 병원 {}"
						+ " | 정리 {}행 | 품질 이슈 {}{}",
				statusText(result.status()), result.snapshotId(), result.targetItems(),
				result.calls(), result.retries(), result.perSecondLimited(),
				result.elapsed().toMillis(), result.apiElapsed().toMillis(), result.dbElapsed().toMillis(),
				result.itemCodes(), result.statRows(),
				result.received(), result.loaded(), result.excluded(), result.emptyItems(),
				result.unmatchedYkiho() == null ? "확인 불가" : result.unmatchedYkiho(),
				result.deleted(), result.issues(), result.reason() == null ? "" : " | 사유 " + result.reason());
		if (!result.succeeded()) {
			throw new IllegalStateException("비급여 수집 실패: " + result.reason());
		}
	}

	private static String statusText(SnapshotStatus status) {
		return switch (status) {
			case ACTIVE -> "완료";
			case TRIAL -> "시험 실행 완료";
			default -> "실패";
		};
	}

	/** 값이 하나인 옵션. 없으면 null */
	private static String single(ApplicationArguments args, String name) {
		List<String> values = args.getOptionValues(name);
		if (values == null) {
			return null;
		}
		if (values.size() != 1 || values.getFirst().isBlank()) {
			throw new IllegalArgumentException("--" + name + "는 값 하나로 넘겨야 합니다");
		}
		return values.getFirst();
	}

}
