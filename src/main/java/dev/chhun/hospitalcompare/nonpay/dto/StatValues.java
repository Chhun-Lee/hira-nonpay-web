package dev.chhun.hospitalcompare.nonpay.dto;

/**
 * 심평원 통계 한 칸(원). 값이 없는 칸은 null이다.
 *
 * @param key    통계 접미사(All, Usgh, Sl, Cln …)
 * @param name   표시 이름(전국, 상급종합, 서울, 전남광주 …)
 * @param median 심평원의 중간값(middAvg)
 */
public record StatValues(String key, String name, Long min, Long max, Long average, Long median) {

	public static StatValues of(NonpayStatRecord stat, String name) {
		return new StatValues(stat.dimKey(), name, stat.minPrice(), stat.maxPrice(), stat.avgPrice(),
				stat.medianPrice());
	}

}
