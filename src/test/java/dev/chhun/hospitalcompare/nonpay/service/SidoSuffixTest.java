package dev.chhun.hospitalcompare.nonpay.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.nonpay.entity.StatDimension;
import java.util.List;
import org.junit.jupiter.api.Test;

class SidoSuffixTest {

	@Test
	void 전남광주_360000은_Cln이고_전남광주로_표시한다() {
		assertThat(SidoSuffix.of("360000")).isEqualTo("Cln");
		assertThat(StatDimension.SIDO.displayName(SidoSuffix.of("360000"))).isEqualTo("전남광주");
	}

	@Test
	void 세종_410000은_Sj다() {
		assertThat(SidoSuffix.of("410000")).isEqualTo("Sj");
	}

	@Test
	void 모든_시도_접미사는_통계가_아는_접미사다() {
		List<String> codes = List.of("110000", "210000", "220000", "230000", "240000", "250000", "260000", "310000",
				"320000", "330000", "340000", "350000", "360000", "370000", "380000", "390000", "410000");
		for (String code : codes) {
			assertThat(StatDimension.SIDO.knows(SidoSuffix.of(code))).as(code).isTrue();
		}
	}

	@Test
	void 모르는_코드나_null은_null이다() {
		assertThat(SidoSuffix.of("999999")).isNull();
		assertThat(SidoSuffix.of(null)).isNull();
	}

}
