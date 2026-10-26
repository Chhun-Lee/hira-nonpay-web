package dev.chhun.hospitalcompare.hospital.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.dto.WeeklyHours;
import dev.chhun.hospitalcompare.detail.repository.DetailUpsertRepository;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * 기준점 (37.5, 127.0), 반경 1km, 고정 시계 2026-10-07(수) 10:00.
 * 가까운 순: Y-CLINIC 의원 약 70m, Y-OPEN 병원 약 100m(월~금 09~18시), Y-NODTL 요양병원 약 200m(세부정보 없음),
 * Y-CLOSED 종합병원 약 300m(월요일만 진료).
 */
@ApiIntegrationTest
class HospitalOpenNowApiTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	DetailUpsertRepository upsertRepository;

	@BeforeEach
	void setUp() {
		for (String table : List.of("hospital_detail", "hospital_subject", "hospital", "snapshot")) {
			jdbcTemplate.update("delete from " + table);
		}
		long hospitals = insertSnapshot("HOSPITAL_LIST");
		long detail = insertSnapshot("HOSPITAL_DETAIL");
		insertHospital(hospitals, "Y-CLINIC", "31", 37.5005, 127.0005);
		insertHospital(hospitals, "Y-OPEN", "21", 37.5009, 127.0);
		insertHospital(hospitals, "Y-NODTL", "28", 37.4982, 127.0);
		insertHospital(hospitals, "Y-CLOSED", "11", 37.5, 127.0034);
		upsertRepository.upsertDetails(detail, List.of(
				detail("Y-OPEN", List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
						DayOfWeek.FRIDAY)),
				detail("Y-CLOSED", List.of(DayOfWeek.MONDAY))));
	}

	@Test
	void openNow가_없으면_병원급에만_지금_상태를_붙인다() {
		var response = assertThat(search()).hasStatusOk().bodyJson();

		response.extractingPath("$.hospitals[*].ykiho").asArray()
				.containsExactly("Y-CLINIC", "Y-OPEN", "Y-NODTL", "Y-CLOSED");
		response.extractingPath("$.hospitals[0].openStatus").isNull();
		response.extractingPath("$.hospitals[1].openStatus.status").isEqualTo("OPEN");
		response.extractingPath("$.hospitals[2].openStatus.reason").isEqualTo("진료시간 정보 없음");
		response.extractingPath("$.hospitals[3].openStatus.reason").isEqualTo("오늘 휴진");
		response.extractingPath("$.openNow").isNull();
	}

	@Test
	void openNow면_진료_중인_병원급만_남기고_정보_없는_수와_판정_시각을_준다() {
		var response = assertThat(search().param("openNow", "true")).hasStatusOk().bodyJson();

		response.extractingPath("$.hospitals[*].ykiho").asArray().containsExactly("Y-OPEN");
		response.extractingPath("$.openNow.openCount").isEqualTo(1);
		response.extractingPath("$.openNow.unknownCount").isEqualTo(1);
		response.extractingPath("$.openNow.judgedAt").asString().startsWith("2026-10-07T10:00");
	}

	@Test
	void openNow와_종별을_함께_주면_둘_다_적용한다() {
		var response = assertThat(search().param("openNow", "true").param("clCd", "31")).hasStatusOk().bodyJson();

		response.extractingPath("$.hospitals").asArray().isEmpty();
		response.extractingPath("$.openNow.unknownCount").isEqualTo(0);
	}

	@Test
	void 상세_ACTIVE가_없으면_openNow는_비고_병원급은_모두_정보_없음이다() {
		jdbcTemplate.update("delete from snapshot where source = 'HOSPITAL_DETAIL'");

		var response = assertThat(search().param("openNow", "true")).hasStatusOk().bodyJson();

		response.extractingPath("$.hospitals").asArray().isEmpty();
		response.extractingPath("$.openNow.unknownCount").isEqualTo(3);
	}

	private MockMvcRequestBuilder search() {
		return mvc.get().uri("/api/hospitals").param("lat", "37.5").param("lng", "127.0").param("radius", "1000");
	}

	private long insertSnapshot(String source) {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count) values (?, 'ACTIVE', '2026-10-06', 0)
				""", source);
		return jdbcTemplate.queryForObject("select max(id) from snapshot", Long.class);
	}

	private void insertHospital(long snapshotId, String ykiho, String clCd, double lat, double lng) {
		jdbcTemplate.update("""
				insert into hospital (snapshot_id, ykiho, name, cl_cd, latitude, longitude) values (?, ?, ?, ?, ?, ?)
				""", snapshotId, ykiho, ykiho + "병원", clCd, lat, lng);
	}

	/** 주어진 요일 09:00~18:00 */
	private static HospitalDetailRecord detail(String ykiho, List<DayOfWeek> openDays) {
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(DayOfWeek.class);
		openDays.forEach(day -> days.put(day, new OpenRange(540, 1080)));
		return new HospitalDetailRecord(ykiho, new WeeklyHours(days, Set.of()), null, null, null, null, null, null,
				null, null, null, null, null);
	}

}
