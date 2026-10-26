package dev.chhun.hospitalcompare.detail.controller;

import dev.chhun.hospitalcompare.detail.dto.HospitalDetailResponse;
import dev.chhun.hospitalcompare.detail.service.HospitalDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "병원 조회")
@RestController
@RequestMapping("/api/hospitals")
class HospitalDetailController {

	private final HospitalDetailService hospitalDetailService;

	HospitalDetailController(HospitalDetailService hospitalDetailService) {
		this.hospitalDetailService = hospitalDetailService;
	}

	@Operation(summary = "병원 상세",
			description = "기본정보, 진료시간·진료과목(병원급), 이 병원의 비급여 가격, 지금 진료 상태. 병원 목록에 없으면 404.")
	@GetMapping("/{ykiho}")
	HospitalDetailResponse detail(
			@Parameter(description = "암호화 요양기호(영문·숫자)")
			@PathVariable @Pattern(regexp = "[A-Za-z0-9]{1,128}") String ykiho) {
		return hospitalDetailService.find(ykiho)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "병원이 없습니다"));
	}

}
