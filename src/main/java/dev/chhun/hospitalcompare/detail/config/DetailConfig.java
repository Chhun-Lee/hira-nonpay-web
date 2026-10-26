package dev.chhun.hospitalcompare.detail.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 상세정보 설정. 수집기(대상)와 조회 서버(병원급 판단, 지금 진료 중 판정)가 함께 쓴다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({HospitalLevelProperties.class, HoursProperties.class})
class DetailConfig {

	/** 지금 진료 중 판정의 기준 시각(한국). 테스트는 고정 시각으로 바꾼다 */
	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}

}
