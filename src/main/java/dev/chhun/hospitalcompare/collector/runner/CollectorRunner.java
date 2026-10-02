package dev.chhun.hospitalcompare.collector.runner;

import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.collector.service.HospitalCollector;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * collector 프로파일 진입점. 인자가 없으면 전국, --sgguCd=110001이면 그 시군구만 수집한다(개발용).
 * 수집이 끝나면 프로세스가 종료되고, 실패하면 예외로 0이 아닌 종료 코드를 남긴다.
 */
@Component
@Profile("collector")
@ConditionalOnProperty(name = "collector.run-on-startup", havingValue = "true", matchIfMissing = true)
class CollectorRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

	private final HospitalCollector hospitalCollector;

	CollectorRunner(HospitalCollector hospitalCollector) {
		this.hospitalCollector = hospitalCollector;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<String> sgguCds = args.getOptionValues("sgguCd");
		if (sgguCds != null && (sgguCds.size() != 1 || sgguCds.getFirst().isBlank())) {
			throw new IllegalArgumentException("시군구는 --sgguCd=110001 형식으로 하나만 넘기거나, 전국이면 빼야 합니다");
		}

		CollectResult result = hospitalCollector.collect(sgguCds == null ? null : sgguCds.getFirst());
		log.info("수집 {} 범위={} 스냅샷={} | 호출 {}회(재시도 {}, 초당 한도 {}) | 소요 {}ms(API 합 {}ms, DB 합 {}ms)"
						+ " | 전체 {}건, 수신 {}건, 적재 {}건, 정리 {}행 | 품질 이슈 {}",
				result.succeeded() ? "완료" : "실패", result.scope(), result.snapshotId(),
				result.calls(), result.retries(), result.perSecondLimited(),
				result.elapsed().toMillis(), result.apiElapsed().toMillis(), result.dbElapsed().toMillis(),
				result.totalCount(), result.received(), result.loaded(), result.deleted(), result.issues());
		if (!result.succeeded()) {
			throw new IllegalStateException("수집 실패: " + result.failureReason());
		}
	}

}
