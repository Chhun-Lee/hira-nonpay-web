package dev.chhun.hospitalcompare.hospital.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 기준점 (37.5, 127.0), 반경 500m.
 * 가까운의원 약 100m(북), 종합병원 약 300m(동)은 반경 안이다.
 * 모서리의원(약 627m)은 바운딩 박스에는 들지만 반경 밖이고, 먼병원·좌표없는의원·다른 스냅샷 행은 빠져야 한다.
 */
@ApiIntegrationTest
class HospitalQueryApiTest {

	private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("delete from hospital");
		jdbcTemplate.update("delete from snapshot");
		long active = insertSnapshot("ACTIVE", "2026-10-02", 5);
		long retired = insertSnapshot("RETIRED", "2026-09-25", 1);

		insertHospital(active, "Y-NEAR", "가까운의원", "31", 37.5009, 127.0);
		insertHospital(active, "Y-EAST", "종합병원", "11", 37.5, 127.0034);
		insertHospital(active, "Y-CORNER", "모서리의원", "31", 37.5040, 127.0050);
		insertHospital(active, "Y-FAR", "먼병원", "21", 37.6, 127.0);
		insertHospital(active, "Y-NOPOS", "좌표없는의원", "31", null, null);
		insertHospital(retired, "Y-NEAR", "지난스냅샷의원", "31", 37.5009, 127.0);
	}

	@Test
	void 반경_안의_기관을_가까운_순으로_돌려준다() {
		var response = assertThat(mvc.get().uri("/api/hospitals")
				.param("lat", "37.5").param("lng", "127.0").param("radius", "500"))
				.hasStatusOk()
				.bodyJson();

		response.extractingPath("$.baseDate").isEqualTo("2026-10-02");
		response.extractingPath("$.hospitals[*].name").asArray().containsExactly("가까운의원", "종합병원");
		response.extractingPath("$.hospitals[0].distanceMeters").asNumber().satisfies(
				distance -> assertThat(distance.intValue()).isBetween(98, 102));
		response.extractingPath("$.hospitals[1].distanceMeters").asNumber().satisfies(
				distance -> assertThat(distance.intValue()).isBetween(297, 302));
	}

	@Test
	void 반경_안의_기관이_많아도_가까운_500곳까지만_돌려준다() {
		long active = jdbcTemplate.queryForObject("select id from snapshot where status = 'ACTIVE'", Long.class);
		List<Object[]> rows = IntStream.range(0, 501)
				.mapToObj(i -> new Object[] {active, "Y-MANY-" + i, "많은의원" + i, "31", 37.5 + i * 0.000001, 127.0})
				.toList();
		jdbcTemplate.batchUpdate("""
				insert into hospital (snapshot_id, ykiho, name, cl_cd, latitude, longitude)
				values (?, ?, ?, ?, ?, ?)
				""", rows);

		assertThat(mvc.get().uri("/api/hospitals")
				.param("lat", "37.5").param("lng", "127.0").param("radius", "500"))
				.hasStatusOk()
				.bodyJson().extractingPath("$.hospitals").asArray().hasSize(500);
	}

	@Test
	void 종별코드로_거른다() {
		assertThat(mvc.get().uri("/api/hospitals")
				.param("lat", "37.5").param("lng", "127.0").param("radius", "500").param("clCd", "11"))
				.hasStatusOk()
				.bodyJson().extractingPath("$.hospitals[*].name").asArray().containsExactly("종합병원");
	}

	@Test
	void 브라우저가_XML을_받겠다고_해도_JSON으로_응답한다() {
		assertThat(mvc.get().uri("/api/hospitals")
				.param("lat", "37.5").param("lng", "127.0").param("radius", "500")
				.header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
				.hasStatusOk()
				.hasContentType(MediaType.APPLICATION_JSON);
	}

	@Test
	void XML만_받겠다고_하면_406() {
		assertThat(mvc.get().uri("/api/hospitals")
				.param("lat", "37.5").param("lng", "127.0").param("radius", "500")
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE))
				.hasStatus(HttpStatus.NOT_ACCEPTABLE);
	}

	@Test
	void 위경도가_없으면_400() {
		assertThat(mvc.get().uri("/api/hospitals").param("radius", "500"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void 반경이_허용_범위를_벗어나면_400() {
		assertThat(mvc.get().uri("/api/hospitals")
				.param("lat", "37.5").param("lng", "127.0").param("radius", "50000"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void active_스냅샷이_없으면_빈_목록이다() {
		jdbcTemplate.update("delete from snapshot where status = 'ACTIVE'");

		var response = assertThat(mvc.get().uri("/api/hospitals")
				.param("lat", "37.5").param("lng", "127.0").param("radius", "500"))
				.hasStatusOk()
				.bodyJson();
		response.extractingPath("$.baseDate").isNull();
		response.extractingPath("$.hospitals").asArray().isEmpty();
	}

	@Test
	void active_스냅샷의_기준일과_건수를_돌려준다() {
		var response = assertThat(mvc.get().uri("/api/meta/snapshot"))
				.hasStatusOk()
				.bodyJson();
		response.extractingPath("$.baseDate").isEqualTo("2026-10-02");
		response.extractingPath("$.recordCount").isEqualTo(5);
	}

	@Test
	void active_스냅샷이_없으면_메타는_404() {
		jdbcTemplate.update("delete from snapshot where status = 'ACTIVE'");

		assertThat(mvc.get().uri("/api/meta/snapshot"))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	private long insertSnapshot(String status, String baseDate, int recordCount) {
		jdbcTemplate.update("insert into snapshot (base_date, status, record_count) values (?, ?, ?)",
				baseDate, status, recordCount);
		return jdbcTemplate.queryForObject("select id from snapshot where status = ?", Long.class, status);
	}

	private void insertHospital(long snapshotId, String ykiho, String name, String clCd, Double lat, Double lng) {
		jdbcTemplate.update("""
				insert into hospital (snapshot_id, ykiho, name, cl_cd, latitude, longitude)
				values (?, ?, ?, ?, ?, ?)
				""", snapshotId, ykiho, name, clCd, lat, lng);
	}

	@Test
	void 비급여_ACTIVE가_있어도_기준일은_병원_목록_스냅샷을_따른다() {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count)
				values ('NONPAY', 'ACTIVE', '2026-10-03', 9)
				""");

		assertThat(mvc.get().uri("/api/meta/snapshot"))
				.hasStatusOk()
				.bodyJson().extractingPath("$.baseDate").isEqualTo("2026-10-02");
	}

}
