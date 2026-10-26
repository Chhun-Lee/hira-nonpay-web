package dev.chhun.hospitalcompare.hira.dto;

import java.util.List;

/** 상세정보 응답 한 페이지. 기관 하나의 응답은 한 페이지에 다 오는 것이 보통이다(2026-10-05 최대 35행). */
public record DetailPage<T>(List<T> items, int totalCount) {

	public DetailPage {
		items = List.copyOf(items);
	}

}
