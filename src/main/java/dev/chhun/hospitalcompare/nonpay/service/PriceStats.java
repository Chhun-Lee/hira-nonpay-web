package dev.chhun.hospitalcompare.nonpay.service;

import dev.chhun.hospitalcompare.nonpay.dto.LocalPriceStats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;
import java.util.stream.LongStream;

/**
 * 반경 안 병원 가격으로 기준값을 계산한다. 병원마다 대표값은 최소가(목록 정렬 기준과 같다)다.
 * 표본이 너무 적으면 중앙값이 의미가 없어 3곳 미만이면 계산하지 않는다.
 */
public final class PriceStats {

	static final int MIN_COUNT = 3;

	private PriceStats() {
	}

	/** @return 병원이 3곳 미만이면 empty */
	public static <T> Optional<LocalPriceStats> of(List<T> hospitals, ToLongFunction<T> minPrice,
			ToLongFunction<T> maxPrice) {
		if (hospitals.size() < MIN_COUNT) {
			return Optional.empty();
		}
		long[] mins = hospitals.stream().mapToLong(minPrice).sorted().toArray();
		long max = hospitals.stream().mapToLong(maxPrice).max().orElseThrow();
		int count = mins.length;
		long median = count % 2 == 1
				? mins[count / 2]
				: roundHalfUp(mins[count / 2 - 1] + mins[count / 2], 2);
		long average = roundHalfUp(LongStream.of(mins).sum(), count);
		return Optional.of(new LocalPriceStats(count, median, average, mins[0], max));
	}

	private static long roundHalfUp(long sum, long count) {
		return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).longValueExact();
	}

}
