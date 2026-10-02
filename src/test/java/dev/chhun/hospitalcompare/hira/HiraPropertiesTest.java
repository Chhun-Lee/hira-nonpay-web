package dev.chhun.hospitalcompare.hira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

}
