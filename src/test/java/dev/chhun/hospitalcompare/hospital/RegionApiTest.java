package dev.chhun.hospitalcompare.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 강남구: 역삼동 2곳(37.50/127.03, 37.52/127.05), 논현동 1곳(37.51/127.02), 읍면동 없는 1곳(37.53/127.06),
 * 좌표 없는 1곳. 중랑구 망우동 1곳. 지난 스냅샷의 강남구 삼성동 1곳은 나오면 안 된다.
 */
@ApiIntegrationTest
class RegionApiTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("delete from hospital");
		jdbcTemplate.update("delete from snapshot");
		long active = insertSnapshot("ACTIVE");
		long retired = insertSnapshot("RETIRED");

		insert(active, "Y1", "110001", "강남구", "역삼동", 37.50, 127.03);
		insert(active, "Y2", "110001", "강남구", "역삼동", 37.52, 127.05);
		insert(active, "Y3", "110001", "강남구", "논현동", 37.51, 127.02);
		insert(active, "Y4", "110001", "강남구", null, 37.53, 127.06);
		insert(active, "Y5", "110001", "강남구", "역삼동", null, null);
		insert(active, "Y6", "110019", "중랑구", "망우동", 37.60, 127.09);
		insert(retired, "Y7", "110001", "강남구", "삼성동", 37.51, 127.06);
	}

	@Test
	void 시군구와_동을_이름순으로_중심_좌표와_함께_돌려준다() {
		var body = assertThat(mvc.get().uri("/api/regions")).hasStatusOk().bodyJson();

		body.extractingPath("$[*].sgguCdNm").asArray().containsExactly("강남구", "중랑구");
		body.extractingPath("$[0].sidoCdNm").isEqualTo("서울");
		body.extractingPath("$[0].sgguCd").isEqualTo("110001");
		body.extractingPath("$[0].dongs[*].name").asArray().containsExactly("논현동", "역삼동");
	}

	@Test
	void 시군구_중심은_좌표가_있는_기관_전체의_평균이다() {
		// 읍면동 없는 Y4는 들어가고, 좌표 없는 Y5와 지난 스냅샷 Y7은 빠진다.
		var body = assertThat(mvc.get().uri("/api/regions")).hasStatusOk().bodyJson();

		body.extractingPath("$[0].latitude").asNumber()
				.satisfies(value -> assertThat(value.doubleValue()).isCloseTo(37.515, within(1e-9)));
		body.extractingPath("$[0].longitude").asNumber()
				.satisfies(value -> assertThat(value.doubleValue()).isCloseTo(127.04, within(1e-9)));
	}

	@Test
	void 동_중심은_그_동_기관의_평균이다() {
		var body = assertThat(mvc.get().uri("/api/regions")).hasStatusOk().bodyJson();

		body.extractingPath("$[0].dongs[1].latitude").asNumber()
				.satisfies(value -> assertThat(value.doubleValue()).isCloseTo(37.51, within(1e-9)));
		body.extractingPath("$[0].dongs[1].longitude").asNumber()
				.satisfies(value -> assertThat(value.doubleValue()).isCloseTo(127.04, within(1e-9)));
	}

	@Test
	void active_스냅샷이_없으면_빈_배열이다() {
		jdbcTemplate.update("delete from snapshot where status = 'ACTIVE'");

		assertThat(mvc.get().uri("/api/regions"))
				.hasStatusOk()
				.bodyJson().extractingPath("$").asArray().isEmpty();
	}

	private long insertSnapshot(String status) {
		jdbcTemplate.update("insert into snapshot (base_date, status, record_count) values ('2026-10-02', ?, 0)", status);
		return jdbcTemplate.queryForObject("select id from snapshot where status = ?", Long.class, status);
	}

	private void insert(long snapshotId, String ykiho, String sgguCd, String sgguCdNm, String emdongNm,
			Double latitude, Double longitude) {
		jdbcTemplate.update("""
				insert into hospital (snapshot_id, ykiho, name, sido_cd_nm, sggu_cd, sggu_cd_nm, emdong_nm, latitude, longitude)
				values (?, ?, ?, '서울', ?, ?, ?, ?, ?)
				""", snapshotId, ykiho, ykiho + "병원", sgguCd, sgguCdNm, emdongNm, latitude, longitude);
	}

}
