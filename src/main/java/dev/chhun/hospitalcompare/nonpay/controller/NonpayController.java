package dev.chhun.hospitalcompare.nonpay.controller;

import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemSearchResponse;
import dev.chhun.hospitalcompare.nonpay.dto.PriceCompareResponse;
import dev.chhun.hospitalcompare.nonpay.service.NonpayQueryService;
import dev.chhun.hospitalcompare.nonpay.service.PriceSort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 비급여 항목 검색과 항목별 가격 비교. 병원급 이상만 있다(의원급은 Open API 제공 범위 밖).
 * 사용자 위치(lat, lng)는 위치정보법상 저장·로그하지 않는다. 이 경로에서 좌표를 로그로 남기지 말 것.
 */
@Tag(name = "비급여 비교")
@RestController
@RequestMapping("/api/noncovered")
class NonpayController {

	private final NonpayQueryService nonpayQueryService;

	NonpayController(NonpayQueryService nonpayQueryService) {
		this.nonpayQueryService = nonpayQueryService;
	}

	@Operation(summary = "비급여 항목 검색",
			description = "항목명 부분일치. 가격을 공개한 병원이 있는 항목만, 공개 병원이 많은 순으로 최대 20개.")
	@GetMapping("/items")
	NonpayItemSearchResponse searchItems(
			@Parameter(description = "검색어(1~50자)", example = "MRI")
			@RequestParam @NotBlank @Size(max = 50) String q) {
		return nonpayQueryService.searchItems(q);
	}

	@Operation(summary = "빠른 선택 항목",
			description = "설정한 대표 항목(설정 순서). 비급여 ACTIVE에 없거나 가격을 공개한 병원이 없는 항목은 뺀다.")
	@GetMapping("/items/featured")
	NonpayItemSearchResponse featuredItems() {
		return nonpayQueryService.featuredItems();
	}

	@Operation(summary = "항목별 가격 비교",
			description = "반경 안에서 이 항목 가격을 공개한 병원(최대 500곳)과 반경·심평원 기준값. 모르는 항목이면 404.")
	@GetMapping("/prices")
	PriceCompareResponse comparePrices(
			@Parameter(description = "비급여 항목 코드", example = "HE1110000")
			@RequestParam @NotBlank @Size(max = 50) String itemCd,
			@Parameter(description = "위도", example = "37.4979")
			@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
			@Parameter(description = "경도", example = "127.0276")
			@RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
			@Parameter(description = "반경(m), 1 ~ 20,000", example = "5000")
			@RequestParam @Min(1) @Max(20_000) int radius,
			@Parameter(description = "price(가격순, 기본) 또는 distance(거리순)")
			@RequestParam(defaultValue = "price") @Pattern(regexp = "price|distance") String sort) {
		return nonpayQueryService.comparePrices(itemCd, lat, lng, radius, PriceSort.from(sort))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "비급여 항목이 없습니다: " + itemCd));
	}

}
