package dev.chhun.hospitalcompare.collector.runner;

import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.collector.service.HospitalCollector;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * collector 프로파일 진입점. 예: --spring.profiles.active=collector,local --sgguCd=110001
 * 수집이 끝나면 프로세스가 종료되고, 실패하면 예외로 0이 아닌 종료 코드를 남긴다.
 */
@Component
@Profile("collector")
class CollectorRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

	private final HospitalCollector hospitalCollector;

	CollectorRunner(HospitalCollector hospitalCollector) {
		this.hospitalCollector = hospitalCollector;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<String> sgguCds = args.getOptionValues("sgguCd");
		if (sgguCds == null || sgguCds.size() != 1 || sgguCds.getFirst().isBlank()) {
			throw new IllegalArgumentException("수집할 시군구 하나를 --sgguCd=110001 형식으로 넘겨야 합니다");
		}

		CollectResult result = hospitalCollector.collect(sgguCds.getFirst());
		log.info("수집 완료 sgguCd={} 호출 {}회, 수신 {}건, 소요 {}ms(DB 쓰기 {}ms) / 스냅샷 {} 전체 {}건",
				result.sgguCd(), result.calls(), result.received(),
				result.elapsed().toMillis(), result.writeElapsed().toMillis(),
				result.snapshotId(), result.snapshotRecordCount());
	}

}
