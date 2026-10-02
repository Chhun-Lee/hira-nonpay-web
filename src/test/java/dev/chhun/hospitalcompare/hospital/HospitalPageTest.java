package dev.chhun.hospitalcompare.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
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

}
