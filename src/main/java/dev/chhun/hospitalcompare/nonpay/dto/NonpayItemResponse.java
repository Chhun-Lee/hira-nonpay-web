package dev.chhun.hospitalcompare.nonpay.dto;

/**
 * 비급여 항목 하나(응답).
 *
 * @param category 중분류 이름
 * @param detail   전체 이름에서 첫 '/' 앞(중분류)을 뗀 나머지. 화면에 짧게 쓴다
 * @param label    빠른 선택 버튼 이름. 검색 결과에서는 null
 */
public record NonpayItemResponse(String code, String name, String category, String detail, long hospitalCount,
		String label) {

	public static NonpayItemResponse of(NonpayItemSummary item, String label) {
		String name = item.name();
		int slash = name == null ? -1 : name.indexOf('/');
		String detail = slash < 0 ? name : name.substring(slash + 1);
		return new NonpayItemResponse(item.code(), name, item.category(), detail, item.hospitalCount(), label);
	}

}
