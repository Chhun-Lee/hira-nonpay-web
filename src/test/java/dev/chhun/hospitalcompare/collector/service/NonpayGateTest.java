package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NonpayGateTest {

	private final NonpayGate gate = new NonpayGate(0.8, 0.05, 0.05);

	@Test
	void 기준을_모두_넘으면_통과한다() {
		assertThat(gate.check(1000, 900, 950, 10, 100, 2)).isEmpty();
	}

	@Test
	void 첫_실행은_이전_건수와_비교하지_않는다() {
		assertThat(gate.check(null, 10, 10, 0, 1, 0)).isEmpty();
	}

	@Test
	void 가격이_하나도_없으면_실패한다() {
		assertThat(gate.check(null, 0, 0, 0, 711, 711)).get().asString().contains("적재된 가격이 없습니다");
	}

	@Test
	void 제외된_행이_5퍼센트를_넘으면_실패한다() {
		assertThat(gate.check(null, 940, 1000, 60, 10, 0)).get().asString().contains("응답 형식 변경");
	}

	@Test
	void 이전_ACTIVE의_80퍼센트에_못_미치면_실패한다() {
		assertThat(gate.check(1000, 799, 799, 0, 10, 0)).get().asString().contains("부분 수집");
	}

	@Test
	void 가격이_없는_항목이_5퍼센트를_넘으면_실패한다() {
		assertThat(gate.check(null, 1000, 1000, 0, 100, 6)).get().asString().contains("가격이 하나도 없는 항목");
	}

}
