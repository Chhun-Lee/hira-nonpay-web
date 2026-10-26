package dev.chhun.hospitalcompare.nonpay.dto;

/**
 * 한 병원이 공개한 비급여 항목 하나와 그 가격(병원 상세용).
 *
 * @param name     항목 전체 이름(중분류/소분류/상세)
 * @param category 중분류 이름
 * @param detail   전체 이름에서 첫 '/' 앞(중분류)을 뗀 나머지. 화면에 짧게 쓴다
 * @param minPrice 원
 * @param maxPrice 원. 등급·부위별로 여러 줄을 공개했으면 최소와 다르다
 */
public record HospitalNonpayItem(String code, String name, String category, String detail, long minPrice,
		long maxPrice) {

	public static HospitalNonpayItem of(String code, String name, String category, long minPrice, long maxPrice) {
		int slash = name == null ? -1 : name.indexOf('/');
		String detail = slash < 0 ? name : name.substring(slash + 1);
		return new HospitalNonpayItem(code, name, category, detail, minPrice, maxPrice);
	}

}
