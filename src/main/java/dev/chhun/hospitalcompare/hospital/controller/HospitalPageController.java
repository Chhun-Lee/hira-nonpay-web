package dev.chhun.hospitalcompare.hospital.controller;

import dev.chhun.hospitalcompare.global.config.KakaoMapProperties;
import dev.chhun.hospitalcompare.hospital.dto.SnapshotResponse;
import dev.chhun.hospitalcompare.hospital.service.HospitalQueryService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 반경 병원 검색 화면. 서버는 페이지 틀과 카카오 키·기준일·결과 상한만 넣고, 검색은 브라우저 JS가 JSON API로 한다.
 */
@Hidden
@Controller
@Profile("api")
class HospitalPageController {

	private final HospitalQueryService hospitalQueryService;
	private final KakaoMapProperties kakaoMapProperties;

	HospitalPageController(HospitalQueryService hospitalQueryService, KakaoMapProperties kakaoMapProperties) {
		this.hospitalQueryService = hospitalQueryService;
		this.kakaoMapProperties = kakaoMapProperties;
	}

	@GetMapping("/")
	String index(Model model) {
		model.addAttribute("kakaoMapKey", kakaoMapProperties.jsKey());
		model.addAttribute("baseDate", hospitalQueryService.activeSnapshot().map(SnapshotResponse::baseDate).orElse(null));
		model.addAttribute("maxResults", HospitalQueryService.MAX_RESULTS);
		return "index";
	}

}
