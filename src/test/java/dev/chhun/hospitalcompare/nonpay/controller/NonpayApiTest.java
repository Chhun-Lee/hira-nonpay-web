package dev.chhun.hospitalcompare.nonpay.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * 기준점 (37.5, 127.0), 반경 500m. 허리 MRI(HE1110000):
 * Y-A 약 100m 300,000원(서울), Y-C 약 200m 100,000원(서울), Y-B 약 300m 100,000~200,000원(서울).
 */
@ApiIntegrationTest
class NonpayApiTest {

	private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
	private static final String MRI = "HE1110000";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		for (String table : List.of("nonpay_price", "nonpay_item", "nonpay_stat", "hospital", "snapshot")) {
			jdbcTemplate.update("delete from " + table);
		}
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count)
				values ('HOSPITAL_LIST', 'ACTIVE', '2026-10-02', 3), ('NONPAY', 'ACTIVE', '2026-10-03', 3)
				""");
		long hospitals = id("HOSPITAL_LIST");
		long nonpay = id("NONPAY");
		insertHospital(hospitals, "Y-A", "가병원", 37.5009, 127.0);
		insertHospital(hospitals, "Y-B", "나병원", 37.5, 127.0034);
		insertHospital(hospitals, "Y-C", "다병원", 37.4982, 127.0);
		jdbcTemplate.update("insert into nonpay_item (snapshot_id, npay_cd, npay_kor_nm, mdiv_cd_nm) values (?, ?, ?, ?)",
				nonpay, MRI, "자기공명영상진단료(MRI-기본검사)/척추-요천추/일반", "자기공명영상진단료(MRI-기본검사)");
		insertPrice(nonpay, "Y-A", 300000, 300000);
		insertPrice(nonpay, "Y-B", 100000, 200000);
		insertPrice(nonpay, "Y-C", 100000, 100000);
		jdbcTemplate.update("""
				insert into nonpay_stat (snapshot_id, npay_cd, dimension, dim_key, min_price, max_price, avg_price,
				                         median_price, std_date)
				values (?, ?, 'CL_TYPE', 'All', 250000, 1001000, 480000, 456450, '2026-09-03'),
				       (?, ?, 'SIDO', 'Sl', 400000, 1001000, 520000, 500000, '2026-09-03')
				""", nonpay, MRI, nonpay, MRI);
	}

	@Test
	void 가격_비교는_기준일_항목_목록_기준값을_준다() {
		var response = assertThat(prices().param("itemCd", MRI))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$.baseDate.hospitals").isEqualTo("2026-10-02");
		response.extractingPath("$.baseDate.nonpay").isEqualTo("2026-10-03");
		response.extractingPath("$.item.code").isEqualTo(MRI);
		response.extractingPath("$.item.detail").isEqualTo("척추-요천추/일반");
		response.extractingPath("$.total").isEqualTo(3);
		response.extractingPath("$.hospitals[*].ykiho").asArray().containsExactly("Y-C", "Y-B", "Y-A");
		response.extractingPath("$.hospitals[1].minPrice").isEqualTo(100000);
		response.extractingPath("$.hospitals[1].maxPrice").isEqualTo(200000);
		response.extractingPath("$.local.count").isEqualTo(3);
		response.extractingPath("$.local.median").isEqualTo(100000);
		response.extractingPath("$.reference.stdDate").isEqualTo("2026-09-03");
		response.extractingPath("$.reference.nationwide.median").isEqualTo(456450);
		response.extractingPath("$.reference.sido.name").isEqualTo("서울");
	}

	@Test
	void 거리순으로_정렬할_수_있다() {
		assertThat(prices().param("itemCd", MRI).param("sort", "distance"))
				.hasStatusOk()
				.bodyJson().extractingPath("$.hospitals[*].ykiho").asArray().containsExactly("Y-A", "Y-C", "Y-B");
	}

	@Test
	void 정렬값이_잘못되면_400() {
		assertThat(prices().param("itemCd", MRI).param("sort", "cheap"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void 모르는_항목은_404() {
		assertThat(prices().param("itemCd", "ZZZ"))
				.hasStatus(HttpStatus.NOT_FOUND)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void 항목코드가_없거나_반경이_범위를_벗어나면_400() {
		assertThat(prices()).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/noncovered/prices")
				.param("itemCd", MRI).param("lat", "37.5").param("lng", "127.0").param("radius", "50000"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void 비급여_ACTIVE가_없으면_가격_비교는_200으로_빈_결과를_준다() {
		jdbcTemplate.update("delete from snapshot where source = 'NONPAY'");

		var response = assertThat(prices().param("itemCd", MRI))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$.item").isNull();
		response.extractingPath("$.hospitals").asArray().isEmpty();
		response.extractingPath("$.total").isEqualTo(0);
		response.extractingPath("$.reference").isNull();
	}

	@Test
	void 항목을_검색한다() {
		var response = assertThat(mvc.get().uri("/api/noncovered/items").param("q", "MRI"))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$.baseDate").isEqualTo("2026-10-03");
		response.extractingPath("$.items[0].code").isEqualTo(MRI);
		response.extractingPath("$.items[0].category").isEqualTo("자기공명영상진단료(MRI-기본검사)");
		response.extractingPath("$.items[0].hospitalCount").isEqualTo(3);
	}

	@Test
	void 검색어가_비었거나_너무_길면_400() {
		assertThat(mvc.get().uri("/api/noncovered/items").param("q", "  ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/noncovered/items").param("q", "가".repeat(51)))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/noncovered/items")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void 빠른_선택_항목은_버튼_이름을_함께_준다() {
		assertThat(mvc.get().uri("/api/noncovered/items/featured"))
				.hasStatusOk()
				.bodyJson().extractingPath("$.items[*].label").asArray().containsExactly("허리 MRI");
	}

	@Test
	void 브라우저가_XML을_받겠다고_해도_JSON으로_응답한다() {
		assertThat(prices().param("itemCd", MRI).header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.hasContentType(MediaType.APPLICATION_JSON);
	}

	private MockMvcRequestBuilder prices() {
		return mvc.get().uri("/api/noncovered/prices").param("lat", "37.5").param("lng", "127.0").param("radius", "500");
	}

	private long id(String source) {
		return jdbcTemplate.queryForObject("select id from snapshot where source = ?", Long.class, source);
	}

	private void insertHospital(long snapshotId, String ykiho, String name, double lat, double lng) {
		jdbcTemplate.update("""
				insert into hospital (snapshot_id, ykiho, name, cl_cd, cl_cd_nm, latitude, longitude)
				values (?, ?, ?, '21', '병원', ?, ?)
				""", snapshotId, ykiho, name, lat, lng);
	}

	private void insertPrice(long snapshotId, String ykiho, long min, long max) {
		jdbcTemplate.update("""
				insert into nonpay_price (snapshot_id, npay_cd, ykiho, min_price, max_price, sido_cd)
				values (?, ?, ?, ?, ?, '110000')
				""", snapshotId, MRI, ykiho, min, max);
	}

}
