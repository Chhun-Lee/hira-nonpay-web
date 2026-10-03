package dev.chhun.hospitalcompare.hira.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HiraPropertiesTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "${HIRA_SERVICE_KEY}"})
	void 서비스키가_없거나_환경변수가_치환되지_않았으면_기동을_막는다(String serviceKey) {
		assertThatThrownBy(() -> new HiraProperties("https://apis.data.go.kr/B551182", serviceKey))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("HIRA_SERVICE_KEY");
	}

	@Test
	void toString에_서비스키를_노출하지_않는다() {
		HiraProperties properties = new HiraProperties("https://apis.data.go.kr/B551182", "fake+Key/For==Test");

		assertThat(properties.toString()).doesNotContain("fake+Key/For==Test");
	}

	@Test
	void 두_인자로_만들면_서비스별_기본값을_쓴다() {
		HiraProperties properties = new HiraProperties("https://apis.data.go.kr/B551182", "fake+Key/For==Test");

		assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
		assertThat(properties.retry()).isEqualTo(HiraProperties.Retry.DEFAULT);
		assertThat(properties.hospInfo()).isEqualTo(new HiraProperties.HospInfo(Duration.ofSeconds(30), 4, 5));
		assertThat(properties.nonpay()).isEqualTo(new HiraProperties.Nonpay(Duration.ofSeconds(120), 4, 5));
	}

	@Test
	void 서비스_하나의_값만_바꿔도_나머지는_그_서비스의_기본값을_쓴다() {
		HiraProperties properties = bind(Map.of(
				"hira.base-url", "https://apis.data.go.kr/B551182",
				"hira.service-key", "fake+Key/For==Test",
				"hira.nonpay.max-concurrency", "8"));

		assertThat(properties.nonpay()).isEqualTo(new HiraProperties.Nonpay(Duration.ofSeconds(120), 8, 5));
		assertThat(properties.hospInfo()).isEqualTo(HiraProperties.HospInfo.DEFAULT);
	}

	@Test
	void 서비스별_읽기_제한은_따로_바인딩된다() {
		HiraProperties properties = bind(Map.of(
				"hira.base-url", "https://apis.data.go.kr/B551182",
				"hira.service-key", "fake+Key/For==Test",
				"hira.hosp-info.read-timeout", "45s",
				"hira.nonpay.read-timeout", "90s"));

		assertThat(properties.hospInfo().readTimeout()).isEqualTo(Duration.ofSeconds(45));
		assertThat(properties.nonpay().readTimeout()).isEqualTo(Duration.ofSeconds(90));
	}

	@Test
	void 동시_실행이_1보다_작으면_기동을_막는다() {
		assertThatThrownBy(() -> new HiraProperties.Nonpay(Duration.ofSeconds(120), 0, 5))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("hira.nonpay.max-concurrency");
	}

	@Test
	void 옛_설정_키가_남아_있으면_기동이_실패한다() {
		new ApplicationContextRunner()
				.withUserConfiguration(HiraPropertiesConfig.class)
				.withPropertyValues(
						"hira.base-url=https://apis.data.go.kr/B551182",
						"hira.service-key=fake+Key/For==Test",
						"hira.max-concurrency=8")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void 알려진_키만_있으면_기동한다() {
		new ApplicationContextRunner()
				.withUserConfiguration(HiraPropertiesConfig.class)
				.withPropertyValues(
						"hira.base-url=https://apis.data.go.kr/B551182",
						"hira.service-key=fake+Key/For==Test",
						"hira.nonpay.max-concurrency=8")
				.run(context -> assertThat(context).hasNotFailed());
	}

	@EnableConfigurationProperties(HiraProperties.class)
	static class HiraPropertiesConfig {
	}

	private static HiraProperties bind(Map<String, String> values) {
		return new Binder(new MapConfigurationPropertySource(values)).bind("hira", HiraProperties.class).get();
	}

}
