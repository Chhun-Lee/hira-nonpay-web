package dev.chhun.hospitalcompare.nonpay.dto;

import dev.chhun.hospitalcompare.nonpay.entity.StatDimension;
import java.time.LocalDate;

/** 펼친 기준값 한 건. 값이 없는 칸은 null */
public record NonpayStatRecord(
		String npayCd,
		StatDimension dimension,
		String dimKey,
		Long minPrice,
		Long maxPrice,
		Long avgPrice,
		Long medianPrice,
		LocalDate stdDate) {
}
