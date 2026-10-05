package dev.chhun.hospitalcompare.nonpay.dto;

/**
 * 비급여 항목 하나와 그 항목 가격을 공개한 병원 수(조회용).
 *
 * @param name     항목 전체 이름(중분류/소분류/상세)
 * @param category 중분류 이름
 */
public record NonpayItemSummary(String code, String name, String category, long hospitalCount) {
}
