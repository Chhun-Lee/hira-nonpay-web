package dev.chhun.hospitalcompare.nonpay.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatDimensionTest {

	@Test
	void 충북은_실제_응답의_Ccb로_읽는다() {
		assertThat(StatDimension.SIDO.knows("Ccb")).isTrue();
		assertThat(StatDimension.SIDO.displayName("Ccb")).isEqualTo("충북");
	}

	@Test
	void 광주가_합쳐진_Cln은_전남광주로_표시한다() {
		assertThat(StatDimension.SIDO.displayName("Cln")).isEqualTo("전남광주");
	}

}
