package dev.chhun.hospitalcompare.nonpay.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param baseDate 비급여 ACTIVE 기준일. 비급여 ACTIVE가 없으면 null
 */
public record NonpayItemSearchResponse(LocalDate baseDate, List<NonpayItemResponse> items) {
}
