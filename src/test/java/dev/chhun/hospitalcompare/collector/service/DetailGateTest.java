package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DetailGateTest {

	private final DetailGate gate = new DetailGate(0.05, 0.8, 0.05);

	@Test
	void 모두_기준_안이면_통과한다() {
		assertThat(gate.check(100, 100, 5, 80, 100, 5)).isEmpty();
	}

	@Test
	void 대상이_없으면_실패한다() {
		assertThat(gate.check(null, 0, 0, 0, 0, 0)).hasValueSatisfying(reason ->
				assertThat(reason).contains("대상 기관이 없습니다"));
	}

	@Test
	void 진료과목이_없는_기관이_5퍼센트를_넘으면_실패한다() {
		assertThat(gate.check(null, 100, 6, 50, 100, 0)).hasValueSatisfying(reason ->
				assertThat(reason).contains("진료과목이 없는 기관 6곳").contains("응답 이상 의심"));
	}

	@Test
	void 세부정보가_있는_기관이_이전의_80퍼센트_미만이면_실패한다() {
		assertThat(gate.check(100, 100, 0, 79, 100, 0)).hasValueSatisfying(reason ->
				assertThat(reason).contains("세부정보가 있는 기관 79곳").contains("부분 수집 의심"));
		assertThat(gate.check(null, 100, 0, 1, 100, 0)).isEmpty();
	}

	@Test
	void 읽지_못한_진료_시각이_5퍼센트를_넘으면_실패한다() {
		assertThat(gate.check(null, 100, 0, 50, 100, 6)).hasValueSatisfying(reason ->
				assertThat(reason).contains("읽지 못한 진료 시각 6칸").contains("형식 변경 의심"));
		assertThat(gate.check(null, 100, 0, 0, 0, 0)).isEmpty();
	}

}
