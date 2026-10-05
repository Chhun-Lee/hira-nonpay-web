package dev.chhun.hospitalcompare.hospital.controller;

import dev.chhun.hospitalcompare.global.config.KakaoMapProperties;
import dev.chhun.hospitalcompare.hospital.dto.SnapshotResponse;
import dev.chhun.hospitalcompare.hospital.service.HospitalQueryService;
import dev.chhun.hospitalcompare.nonpay.config.NonpayUiProperties;
import dev.chhun.hospitalcompare.nonpay.service.NonpayQueryService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 반경 병원 검색 화면. 서버는 페이지 틀과 카카오 키·두 기준일·결과 상한·심평원 안내 주소만 넣고, 검색은 브라우저 JS가 JSON API로 한다.
 */
@Hidden
@Controller
@Profile("api")
class HospitalPageController {

	private final HospitalQueryService hospitalQueryService;
	private final KakaoMapProperties kakaoMapProperties;
	private final String defaultSgguCd;
	private final NonpayQueryService nonpayQueryService;
	private final NonpayUiProperties nonpayUiProperties;

	HospitalPageController(HospitalQueryService hospitalQueryService, KakaoMapProperties kakaoMapProperties,
			@Value("${ui.default-sggu-cd:110001}") String defaultSgguCd, NonpayQueryService nonpayQueryService,
			NonpayUiProperties nonpayUiProperties) {
		this.hospitalQueryService = hospitalQueryService;
		this.kakaoMapProperties = kakaoMapProperties;
		this.defaultSgguCd = defaultSgguCd;
		this.nonpayQueryService = nonpayQueryService;
		this.nonpayUiProperties = nonpayUiProperties;
	}

	@GetMapping("/")
	String index(Model model) {
		model.addAttribute("kakaoMapKey", kakaoMapProperties.jsKey());
		model.addAttribute("baseDate", hospitalQueryService.activeSnapshot().map(SnapshotResponse::baseDate).orElse(null));
		model.addAttribute("maxResults", HospitalQueryService.MAX_RESULTS);
		model.addAttribute("defaultSgguCd", defaultSgguCd);
		model.addAttribute("nonpayBaseDate", nonpayQueryService.activeBaseDate().orElse(null));
		model.addAttribute("hiraNonpayUrl", nonpayUiProperties.hiraNonpayUrl());
		return "index";
	}

}
