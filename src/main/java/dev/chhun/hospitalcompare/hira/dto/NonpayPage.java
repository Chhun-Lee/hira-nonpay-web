package dev.chhun.hospitalcompare.hira.dto;

import java.util.List;

/** 비급여 목록 응답 한 페이지 */
public record NonpayPage<T>(List<T> items, int numOfRows, int pageNo, int totalCount) {

	public NonpayPage {
		items = List.copyOf(items);
	}

}
