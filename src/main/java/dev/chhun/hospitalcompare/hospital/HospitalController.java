package dev.chhun.hospitalcompare.hospital;

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
@RestController
@RequestMapping("/api/hospitals")
class HospitalController {

	private final HospitalQueryService hospitalQueryService;

	HospitalController(HospitalQueryService hospitalQueryService) {
		this.hospitalQueryService = hospitalQueryService;
	}

	/**
	 * 반경 안의 기관을 거리순으로 최대 100곳 돌려준다.
	 *
	 * @param radius 미터. 1 ~ 20,000
	 * @param clCd   종별코드. 없으면 전체
	 */
	@GetMapping
	HospitalSearchResponse search(
			@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
			@RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
			@RequestParam @Min(1) @Max(20_000) int radius,
			@RequestParam(required = false) String clCd) {
		return hospitalQueryService.searchNearby(lat, lng, radius, clCd);
	}

}
