package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SnapshotGateTest {

	private final SnapshotGate gate = new SnapshotGate(0.8, 0.05);

	@Test
	void 이전보다_20퍼센트_넘게_줄면_실패한다() {
		assertThat(gate.check(10, 7, 7, 0)).hasValueSatisfying(reason -> assertThat(reason).contains("7").contains("10"));
	}

	@Test
	void 이전의_80퍼센트_이상이면_통과한다() {
		assertThat(gate.check(10, 9, 9, 0)).isEmpty();
		assertThat(gate.check(10, 8, 8, 0)).isEmpty();
	}

	@Test
	void 첫_수집은_1건_이상이면_통과한다() {
		assertThat(gate.check(null, 3, 3, 0)).isEmpty();
	}

	@Test
	void 적재가_0건이면_실패한다() {
		assertThat(gate.check(null, 0, 0, 0)).isPresent();
	}

	@Test
	void 필수값_누락이_5퍼센트를_넘으면_실패한다() {
		assertThat(gate.check(null, 94, 100, 6)).hasValueSatisfying(reason -> assertThat(reason).contains("필수값"));
		assertThat(gate.check(null, 95, 100, 5)).isEmpty();
	}

}
