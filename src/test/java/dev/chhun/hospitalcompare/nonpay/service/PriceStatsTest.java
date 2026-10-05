package dev.chhun.hospitalcompare.nonpay.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.nonpay.dto.LocalPriceStats;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PriceStatsTest {

	record Row(long min, long max) {
	}

	private static Optional<LocalPriceStats> of(Row... rows) {
		return PriceStats.of(List.of(rows), Row::min, Row::max);
	}

	@Test
	void 홀수면_가운데_최저가가_중앙값이다() {
		assertThat(of(new Row(300, 300), new Row(100, 150), new Row(200, 900)))
				.contains(new LocalPriceStats(3, 200, 200, 100, 900));
	}

	@Test
	void 짝수면_가운데_두_값의_평균을_반올림한다() {
		// 최저가 100, 100, 101, 101 → 중앙값 (100 + 101) / 2 = 100.5 → 101, 평균 402 / 4 = 100.5 → 101
		assertThat(of(new Row(101, 101), new Row(100, 100), new Row(101, 120), new Row(100, 100)))
				.contains(new LocalPriceStats(4, 101, 101, 100, 120));
	}

	@Test
	void 평균은_원_단위로_반올림한다() {
		// (100 + 100 + 101) / 3 = 100.33 → 100
		assertThat(of(new Row(100, 100), new Row(100, 100), new Row(101, 101))).get()
				.extracting(LocalPriceStats::average).isEqualTo(100L);
	}

	@Test
	void 최고는_병원별_최대가로_정한다() {
		assertThat(of(new Row(100, 500), new Row(200, 200), new Row(300, 300))).get()
				.extracting(LocalPriceStats::max).isEqualTo(500L);
	}

	@Test
	void 세_곳_미만이면_계산하지_않는다() {
		assertThat(of(new Row(100, 100), new Row(200, 200))).isEmpty();
		assertThat(of()).isEmpty();
	}

}
