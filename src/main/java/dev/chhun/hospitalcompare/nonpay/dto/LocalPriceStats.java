package dev.chhun.hospitalcompare.nonpay.dto;

/**
 * 반경 안 병원 가격으로 계산한 기준값(원). 병원마다 대표값은 최소가다.
 *
 * @param count   병원 수
 * @param median  대표값의 중앙값. 짝수면 가운데 두 값의 평균(반올림)
 * @param average 대표값의 평균(반올림)
 * @param min     대표값 중 최저
 * @param max     병원별 최대가 중 최고
 */
public record LocalPriceStats(int count, long median, long average, long min, long max) {
}
