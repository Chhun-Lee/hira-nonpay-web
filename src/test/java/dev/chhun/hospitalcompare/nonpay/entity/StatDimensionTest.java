package dev.chhun.hospitalcompare.nonpay.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatDimensionTest {

	@Test
	void 충북은_실제_응답의_Ccb로_읽는다() {
		assertThat(StatDimension.SIDO.knows("Ccb")).isTrue();
		assertThat(StatDimension.SIDO.displayName("Ccb")).isEqualTo("충북");
	}

}
