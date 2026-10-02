package dev.chhun.hospitalcompare.hira.config;

import dev.chhun.hospitalcompare.hira.client.HiraClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * 심평원 API는 collector 프로세스만 호출한다. api 서버에는 클라이언트도 서비스 키도 올리지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("collector")
@EnableConfigurationProperties(HiraProperties.class)
class HiraClientConfig {

	@Bean
	HiraClient hiraClient(RestClient.Builder restClientBuilder, HiraProperties properties) {
		return new HiraClient(restClientBuilder, properties);
	}

}
