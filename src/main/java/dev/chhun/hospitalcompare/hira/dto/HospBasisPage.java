package dev.chhun.hospitalcompare.hira.dto;

import java.util.List;

/**
 * 목록 한 페이지 분량의 기관과 페이지 정보.
 */
public record HospBasisPage(List<HospBasisItem> items, int numOfRows, int pageNo, int totalCount) {

	public HospBasisPage {
		items = List.copyOf(items);
	}

}
