package dev.chhun.hospitalcompare.hospital.controller;

import dev.chhun.hospitalcompare.hospital.dto.RegionResponse;
import dev.chhun.hospitalcompare.hospital.service.HospitalQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지역")
@RestController
@RequestMapping("/api/regions")
class RegionController {

	private final HospitalQueryService hospitalQueryService;

	RegionController(HospitalQueryService hospitalQueryService) {
		this.hospitalQueryService = hospitalQueryService;
	}

	@Operation(summary = "시군구·동 목록과 중심 좌표",
			description = "active 스냅샷에서 좌표가 있는 기관의 위경도 평균. 수집한 시군구만 나온다.")
	@GetMapping
	List<RegionResponse> regions() {
		return hospitalQueryService.regions();
	}

}
