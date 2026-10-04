package dev.chhun.hospitalcompare.hospital.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import dev.chhun.hospitalcompare.nonpay.config.NonpayUiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@ApiIntegrationTest
class HospitalPageTest {

	private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	NonpayUiProperties nonpayUiProperties;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("delete from hospital");
		jdbcTemplate.update("delete from snapshot");
		jdbcTemplate.update("insert into snapshot (base_date, status, record_count) values ('2026-10-02', 'ACTIVE', 0)");
	}

	@Test
	void 브라우저로_열면_지도_키와_고지가_든_HTML_페이지다() {
		assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
				.bodyText()
				.contains("https://dapi.kakao.com/v2/maps/sdk.js?appkey=test-kakao-key")
				.contains("건강보험심사평가원 공공데이터(공공누리 제1유형)")
				.contains("심평원과 무관한 개인 프로젝트입니다")
				.contains("2026-10-02")
				.contains("data-max-results=\"500\"");
	}

	@Test
	void 카카오_SDK는_페이지_파싱을_막지_않도록_JS가_따로_불러온다() {
		// <script src>로 직접 넣으면 SDK 서버가 응답하지 않는 동안 화면 스크립트 전체가 시작하지 못한다.
		assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.bodyText()
				.contains("data-kakao-sdk=\"https://dapi.kakao.com/v2/maps/sdk.js?appkey=test-kakao-key")
				.doesNotContain("<script src=\"https://dapi.kakao.com");
	}

	@Test
	void active_스냅샷이_없으면_기준일은_수집_전() {
		jdbcTemplate.update("delete from snapshot");

		assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.bodyText().contains("수집 전");
	}

	@Test
	void 화면_스크립트를_내려준다() {
		assertThat(mvc.get().uri("/js/app.js"))
				.hasStatusOk()
				.bodyText().contains("createMapView");
	}

	@Test
	void 스타일_파일을_내려준다() {
		assertThat(mvc.get().uri("/css/app.css"))
				.hasStatusOk()
				.bodyText().contains("--form-green");
	}

	@Test
	void 첫_화면_시군구를_설정값으로_넘긴다() {
		assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.bodyText().contains("data-default-sggu-cd=\"110001\"");
	}

	@Test
	void 하단에_병원_목록과_비급여_기준일을_함께_보여_준다() {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count) values ('NONPAY', 'ACTIVE', '2026-10-03', 0)
				""");

		assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.bodyText().contains("병원 목록 2026-10-02 · 비급여 2026-10-03");
	}

	@Test
	void 비급여_ACTIVE가_없으면_비급여는_수집_전() {
		assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.bodyText().contains("병원 목록 2026-10-02 · 비급여 수집 전");
	}

	@Test
	void 의원급은_공개_범위_밖이라고_안내하고_심평원_화면으로_잇는다() {
		assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.bodyText()
				.contains("의원급 가격은 공개 범위 밖이에요")
				.contains("href=\"" + nonpayUiProperties.hiraNonpayUrl() + "\"")
				.contains("rel=\"noopener noreferrer\"");
	}

	@Test
	void 가격_비교_스크립트를_내려준다() {
		assertThat(mvc.get().uri("/js/price-card.js")).hasStatusOk().bodyText().contains("createPriceCard");
		assertThat(mvc.get().uri("/js/api.js")).hasStatusOk().bodyText().contains("comparePrices");
		assertThat(mvc.get().uri("/js/app.js")).hasStatusOk().bodyText().contains("chooseItem");
	}

	@Test
	void 비급여_항목_칸과_반경_5km_10km가_있다() {
		assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.bodyText()
				.contains("id=\"featured\"")
				.contains("id=\"price-card\"")
				.contains("data-radius=\"5000\"")
				.contains("data-radius=\"10000\"");
	}

}
