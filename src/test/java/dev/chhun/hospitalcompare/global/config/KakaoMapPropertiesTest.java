package dev.chhun.hospitalcompare.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class KakaoMapPropertiesTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "${KAKAO_MAP_KEY}"})
	void 카카오_키가_없거나_환경변수가_치환되지_않았으면_기동을_막는다(String jsKey) {
		assertThatThrownBy(() -> new KakaoMapProperties(jsKey))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("KAKAO_MAP_KEY");
	}

}
