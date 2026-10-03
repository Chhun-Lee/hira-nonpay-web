package dev.chhun.hospitalcompare.hira.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.hira.client.HiraClient;
import dev.chhun.hospitalcompare.hira.client.NonpayClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HiraClientConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
			.withUserConfiguration(HiraClientConfig.class)
			.withPropertyValues("hira.base-url=https://apis.data.go.kr/B551182");

	@Test
	void api_프로파일에는_클라이언트를_올리지_않아_서비스키가_없어도_뜬다() {
		runner.withPropertyValues("spring.profiles.active=api")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(HiraClient.class);
				});
	}

	@Test
	void collector_프로파일은_서비스키가_있으면_클라이언트를_올린다() {
		runner.withPropertyValues("spring.profiles.active=collector", "hira.service-key=fake+Key/For==Test")
				.run(context -> {
					assertThat(context).hasSingleBean(HiraClient.class);
					assertThat(context).hasSingleBean(NonpayClient.class);
				});
	}

	@Test
	void collector_프로파일은_서비스키_환경변수가_없으면_기동에_실패한다() {
		// 실제 HIRA_SERVICE_KEY가 설정된 환경에서도 치환되지 않도록 없는 변수명을 쓴다.
		runner.withPropertyValues("spring.profiles.active=collector", "hira.service-key=${HIRA_SERVICE_KEY_UNSET_IN_TEST}")
				.run(context -> assertThat(context).getFailure()
						.rootCause()
						.hasMessageContaining("HIRA_SERVICE_KEY"));
	}

}
