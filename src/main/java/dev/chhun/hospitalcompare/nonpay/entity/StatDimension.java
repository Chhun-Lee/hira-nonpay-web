package dev.chhun.hospitalcompare.nonpay.entity;

import java.util.Map;

/**
 * 비급여 통계의 구분. API 칸 이름의 접미사(dim_key)로 값을 가른다.
 * 실제 응답은 가이드(2019)와 다르게 세종을 Sj로 준다(2026-10-03 탐색).
 */
public enum StatDimension {

	/** 종별. All은 전체 종별 */
	CL_TYPE(Map.of(
			"All", "전체",
			"Usgh", "상급종합",
			"Gnhp", "종합병원",
			"Hosp", "병원",
			"Recu", "요양병원",
			"Dety", "치과병원",
			"Cmdc", "한방병원")),

	/** 시도. All은 전국 */
	SIDO(Map.ofEntries(
			Map.entry("All", "전국"),
			Map.entry("Sl", "서울"),
			Map.entry("Ps", "부산"),
			Map.entry("Ich", "인천"),
			Map.entry("Tg", "대구"),
			Map.entry("Kw", "광주"),
			Map.entry("Dj", "대전"),
			Map.entry("Usn", "울산"),
			Map.entry("Sj", "세종"),
			Map.entry("Kyg", "경기"),
			Map.entry("Kaw", "강원"),
			Map.entry("Ccbk", "충북"),
			Map.entry("Ccn", "충남"),
			Map.entry("Clb", "전북"),
			Map.entry("Cln", "전남"),
			Map.entry("Ksb", "경북"),
			Map.entry("Ksn", "경남"),
			Map.entry("Chj", "제주")));

	private final Map<String, String> names;

	StatDimension(Map<String, String> names) {
		this.names = names;
	}

	public boolean knows(String key) {
		return names.containsKey(key);
	}

	/** 접미사의 표시 이름. 모르는 접미사면 null */
	public String displayName(String key) {
		return names.get(key);
	}

}
