package dev.chhun.hospitalcompare.detail.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.HospitalSubjectRecord;
import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.dto.WeeklyHours;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import dev.chhun.hospitalcompare.detail.repository.DetailUpsertRepository;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 고정 시계 2026-10-07(수) 10:00. 실제 ykiho처럼 영문·숫자만 쓴다.
 * YKIM: 병원(21), 월~금 08:30~17:00·토 08:30~12:30·점심 12:30~13:30, 진료과목 2, 비급여 1.
 * YNODETAIL: 종합병원(11), 세부정보 없음. YCLINIC: 의원(31).
 */
@ApiIntegrationTest
class HospitalDetailApiTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	DetailUpsertRepository upsertRepository;

	@BeforeEach
	void setUp() {
		for (String table : List.of("hospital_detail", "hospital_subject", "nonpay_price", "nonpay_item", "hospital",
				"snapshot")) {
			jdbcTemplate.update("delete from " + table);
		}
		long hospitals = insertSnapshot("HOSPITAL_LIST", "2026-10-02");
		long detail = insertSnapshot("HOSPITAL_DETAIL", "2026-10-06");
		long nonpay = insertSnapshot("NONPAY", "2026-10-03");
		insertHospital(hospitals, "YKIM", "건양의료재단 김안과병원", "21", "병원");
		insertHospital(hospitals, "YNODETAIL", "세부정보없는병원", "11", "종합병원");
		insertHospital(hospitals, "YCLINIC", "동네의원", "31", "의원");

		Map<DayOfWeek, OpenRange> days = new EnumMap<>(DayOfWeek.class);
		for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
				DayOfWeek.FRIDAY)) {
			days.put(day, new OpenRange(510, 1020));
		}
		days.put(DayOfWeek.SATURDAY, new OpenRange(510, 750));
		upsertRepository.upsertDetails(detail, List.of(new HospitalDetailRecord("YKIM", new WeeklyHours(days, Set.of()),
				Lunch.range(750, 810, "12시30분~1시30분"), Lunch.none(null), "08시00분~17시00분", "08시00분~12시00분",
				Note.EMPTY, new Note(NoteKind.CLOSED, "전부 휴진"), false, false, 100, false, "외래진료차량 무료")));
		upsertRepository.upsertSubjects(detail, List.of(new HospitalSubjectRecord("YKIM", "12", "안과", 47),
				new HospitalSubjectRecord("YKIM", "01", "내과", 1)));
		jdbcTemplate.update("insert into nonpay_item (snapshot_id, npay_cd, npay_kor_nm, mdiv_cd_nm) values (?, ?, ?, ?)",
				nonpay, "ABZ010001", "상급병실료/1인실", "상급병실료");
		jdbcTemplate.update("""
				insert into nonpay_price (snapshot_id, npay_cd, ykiho, min_price, max_price) values (?, ?, ?, ?, ?)
				""", nonpay, "ABZ010001", "YKIM", 150000, 150000);
	}

	@Test
	void 병원급_상세는_진료시간_진료과목_비급여와_지금_상태를_준다() {
		var response = assertThat(mvc.get().uri("/api/hospitals/YKIM")).hasStatusOk().bodyJson();

		response.extractingPath("$.name").isEqualTo("건양의료재단 김안과병원");
		response.extractingPath("$.hospitalLevel").isEqualTo(true);
		response.extractingPath("$.openStatus.status").isEqualTo("OPEN");
		response.extractingPath("$.judgedAt").asString().startsWith("2026-10-07T10:00");
		response.extractingPath("$.detail.hours").asArray().hasSize(7);
		response.extractingPath("$.detail.hours[0].day").isEqualTo("MONDAY");
		response.extractingPath("$.detail.hours[0].state").isEqualTo("OPEN_DAY");
		response.extractingPath("$.detail.hours[0].open").isEqualTo("08:30");
		response.extractingPath("$.detail.hours[0].close").isEqualTo("17:00");
		response.extractingPath("$.detail.hours[6].day").isEqualTo("SUNDAY");
		response.extractingPath("$.detail.hours[6].state").isEqualTo("CLOSED_DAY");
		response.extractingPath("$.detail.lunchWeek.start").isEqualTo("12:30");
		response.extractingPath("$.detail.lunchWeek.text").isEqualTo("12시30분~1시30분");
		response.extractingPath("$.detail.holidayNote.kind").isEqualTo("CLOSED");
		response.extractingPath("$.detail.parking.capacity").isEqualTo(100);
		response.extractingPath("$.subjects[*].code").asArray().containsExactly("01", "12");
		response.extractingPath("$.subjects[1].specialists").isEqualTo(47);
		response.extractingPath("$.nonpayItems[0].code").isEqualTo("ABZ010001");
		response.extractingPath("$.nonpayItems[0].detail").isEqualTo("1인실");
		response.extractingPath("$.nonpayItems[0].minPrice").isEqualTo(150000);
		response.extractingPath("$.baseDates.hospitalList").isEqualTo("2026-10-02");
		response.extractingPath("$.baseDates.detail").isEqualTo("2026-10-06");
		response.extractingPath("$.baseDates.nonpay").isEqualTo("2026-10-03");
	}

	@Test
	void 세부정보가_없는_병원급은_상세가_비고_지금_상태는_정보_없음이다() {
		var response = assertThat(mvc.get().uri("/api/hospitals/YNODETAIL")).hasStatusOk().bodyJson();

		response.extractingPath("$.hospitalLevel").isEqualTo(true);
		response.extractingPath("$.detail").isNull();
		response.extractingPath("$.openStatus.status").isEqualTo("UNKNOWN");
		response.extractingPath("$.openStatus.reason").isEqualTo("진료시간 정보 없음");
		response.extractingPath("$.subjects").asArray().isEmpty();
	}

	@Test
	void 의원급은_상세와_비급여를_비워_보낸다() {
		var response = assertThat(mvc.get().uri("/api/hospitals/YCLINIC")).hasStatusOk().bodyJson();

		response.extractingPath("$.hospitalLevel").isEqualTo(false);
		response.extractingPath("$.openStatus").isNull();
		response.extractingPath("$.detail").isNull();
		response.extractingPath("$.nonpayItems").asArray().isEmpty();
	}

	@Test
	void 상세_ACTIVE가_없으면_상세_기준일과_상세가_비고_지금_상태는_정보_없음이다() {
		jdbcTemplate.update("delete from snapshot where source = 'HOSPITAL_DETAIL'");

		var response = assertThat(mvc.get().uri("/api/hospitals/YKIM")).hasStatusOk().bodyJson();

		response.extractingPath("$.baseDates.detail").isNull();
		response.extractingPath("$.detail").isNull();
		response.extractingPath("$.openStatus.status").isEqualTo("UNKNOWN");
		response.extractingPath("$.nonpayItems").asArray().hasSize(1);
	}

	@Test
	void 없는_병원은_404다() {
		assertThat(mvc.get().uri("/api/hospitals/YNONE")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void 영문과_숫자가_아닌_ykiho는_400이다() {
		assertThat(mvc.get().uri("/api/hospitals/bad_ykiho")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	private long insertSnapshot(String source, String baseDate) {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count) values (?, 'ACTIVE', ?, 0)
				""", source, baseDate);
		return jdbcTemplate.queryForObject("select max(id) from snapshot", Long.class);
	}

	private void insertHospital(long snapshotId, String ykiho, String name, String clCd, String clCdNm) {
		jdbcTemplate.update("""
				insert into hospital (snapshot_id, ykiho, name, cl_cd, cl_cd_nm, address, phone, latitude, longitude,
				                      doctor_count)
				values (?, ?, ?, ?, ?, '서울 영등포구', '02-0000-0000', 37.5, 126.9, 61)
				""", snapshotId, ykiho, name, clCd, clCdNm);
	}

}
