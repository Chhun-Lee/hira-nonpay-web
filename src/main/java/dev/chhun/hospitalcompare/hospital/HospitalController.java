package dev.chhun.hospitalcompare.hospital;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 위치(lat, lng)는 위치정보법상 저장·로그하지 않는다. 이 경로에서 좌표를 로그로 남기지 말 것.
 */
@Tag(name = "병원 조회")
@RestController
@RequestMapping("/api/hospitals")
class HospitalController {

	private final HospitalQueryService hospitalQueryService;

	HospitalController(HospitalQueryService hospitalQueryService) {
		this.hospitalQueryService = hospitalQueryService;
	}

	@Operation(summary = "반경 병원 검색", description = "active 스냅샷에서 반경 안의 기관을 거리순으로 최대 100곳 돌려준다.")
	@GetMapping
	HospitalSearchResponse search(
			@Parameter(description = "위도", example = "37.4979")
			@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
			@Parameter(description = "경도", example = "127.0276")
			@RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
			@Parameter(description = "반경(m), 1 ~ 20,000", example = "500")
			@RequestParam @Min(1) @Max(20_000) int radius,
			@Parameter(description = "종별코드. 예: 01 상급종합, 11 종합병원, 21 병원, 31 의원. 비우면 전체")
			@RequestParam(required = false) String clCd) {
		return hospitalQueryService.searchNearby(lat, lng, radius, clCd);
	}

}
