package dev.chhun.hospitalcompare.nonpay.service;

import dev.chhun.hospitalcompare.nonpay.dto.NearbyPrice;
import java.util.Comparator;
import java.util.Locale;

/** 가격 비교 목록 순서 */
public enum PriceSort {

	/** 최소가 → 최대가 → 거리 → ykiho */
	PRICE(Comparator.comparingLong(NearbyPrice::minPrice)
			.thenComparingLong(NearbyPrice::maxPrice)
			.thenComparingInt(NearbyPrice::distanceMeters)
			.thenComparing(NearbyPrice::ykiho)),

	/** 거리 → 최소가 → ykiho */
	DISTANCE(Comparator.comparingInt(NearbyPrice::distanceMeters)
			.thenComparingLong(NearbyPrice::minPrice)
			.thenComparing(NearbyPrice::ykiho));

	private final Comparator<NearbyPrice> order;

	PriceSort(Comparator<NearbyPrice> order) {
		this.order = order;
	}

	Comparator<NearbyPrice> order() {
		return order;
	}

	/** price, distance(대소문자 무시) */
	public static PriceSort from(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
