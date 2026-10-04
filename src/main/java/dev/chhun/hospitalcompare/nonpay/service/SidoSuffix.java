package dev.chhun.hospitalcompare.nonpay.service;

import static java.util.Map.entry;

import java.util.Map;

/**
 * 비급여 가격 행의 시도 코드(심평원 체계) → 시도 통계 접미사(StatDimension.SIDO의 dim_key).
 * 2026년 광주가 전남과 합쳐져 광주 병원도 360000이고, 통계도 Cln 하나로 준다(2026-10-03 실DB 확인).
 * 240000(광주)은 병원 목록에 옛 코드로 남은 기관 때문에 둔다. 통계에 Kw가 없으므로 그 경우 시도 기준값은 비어 있다.
 */
public final class SidoSuffix {

	private static final Map<String, String> SUFFIXES = Map.ofEntries(
			entry("110000", "Sl"),
			entry("210000", "Ps"),
			entry("220000", "Ich"),
			entry("230000", "Tg"),
			entry("240000", "Kw"),
			entry("250000", "Dj"),
			entry("260000", "Usn"),
			entry("310000", "Kyg"),
			entry("320000", "Kaw"),
			entry("330000", "Ccb"),
			entry("340000", "Ccn"),
			entry("350000", "Clb"),
			entry("360000", "Cln"),
			entry("370000", "Ksb"),
			entry("380000", "Ksn"),
			entry("390000", "Chj"),
			entry("410000", "Sj"));

	private SidoSuffix() {
	}

	/** 시도 코드의 통계 접미사. 비어 있거나 모르는 코드면 null */
	public static String of(String sidoCd) {
		return sidoCd == null ? null : SUFFIXES.get(sidoCd);
	}

}
