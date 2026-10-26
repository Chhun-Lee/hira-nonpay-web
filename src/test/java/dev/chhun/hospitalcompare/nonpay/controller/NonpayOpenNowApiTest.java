package dev.chhun.hospitalcompare.nonpay.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.dto.WeeklyHours;
import dev.chhun.hospitalcompare.detail.repository.DetailUpsertRepository;
import java.time.DayOfWeek;
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
 * 기준점 (37.5, 127.0), 반경 500m, 고정 시계 2026-10-07(수) 10:00. 허리 MRI(HE1110000):
 * Y-A 약 100m 300,000원(수요일 진료), Y-C 약 200m 100,000원(세부정보 없음), Y-B 약 300m 100,000~200,000원(월요일만 진료).
 */
@ApiIntegrationTest
class NonpayOpenNowApiTest {

	private static final String MRI = "HE1110000";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	DetailUpsertRepository upsertRepository;

	@BeforeEach
	void setUp() {
		for (String table : List.of("hospital_detail", "hospital_subject", "nonpay_price", "nonpay_item", "nonpay_stat",
				"hospital", "snapshot")) {
			jdbcTemplate.update("delete from " + table);
		}
		long hospitals = insertSnapshot("HOSPITAL_LIST");
		long nonpay = insertSnapshot("NONPAY");
		long detail = insertSnapshot("HOSPITAL_DETAIL");
		insertHospital(hospitals, "Y-A", 37.5009, 127.0);
		insertHospital(hospitals, "Y-B", 37.5, 127.0034);
		insertHospital(hospitals, "Y-C", 37.4982, 127.0);
		jdbcTemplate.update("insert into nonpay_item (snapshot_id, npay_cd, npay_kor_nm, mdiv_cd_nm) values (?, ?, ?, ?)",
				nonpay, MRI, "자기공명영상진단료(MRI-기본검사)/척추-요천추/일반", "자기공명영상진단료(MRI-기본검사)");
		insertPrice(nonpay, "Y-A", 300000, 300000);
		insertPrice(nonpay, "Y-B", 100000, 200000);
		insertPrice(nonpay, "Y-C", 100000, 100000);
		upsertRepository.upsertDetails(detail, List.of(openOn("Y-A", DayOfWeek.WEDNESDAY),
				openOn("Y-B", DayOfWeek.MONDAY)));
	}

	@Test
	void openNow가_없으면_각_병원에_지금_상태를_붙인다() {
		var response = assertThat(prices()).hasStatusOk().bodyJson();

		response.extractingPath("$.hospitals[*].ykiho").asArray().containsExactly("Y-C", "Y-B", "Y-A");
		response.extractingPath("$.hospitals[0].openStatus.status").isEqualTo("UNKNOWN");
		response.extractingPath("$.hospitals[1].openStatus.reason").isEqualTo("오늘 휴진");
		response.extractingPath("$.hospitals[2].openStatus.status").isEqualTo("OPEN");
		response.extractingPath("$.openNow").isNull();
	}

	@Test
	void openNow면_진료_중인_병원만_남기고_기준값과_total은_거르기_전이다() {
		var response = assertThat(prices().param("openNow", "true")).hasStatusOk().bodyJson();

		response.extractingPath("$.hospitals[*].ykiho").asArray().containsExactly("Y-A");
		response.extractingPath("$.total").isEqualTo(3);
		response.extractingPath("$.local.count").isEqualTo(3);
		response.extractingPath("$.openNow.openCount").isEqualTo(1);
		response.extractingPath("$.openNow.unknownCount").isEqualTo(1);
	}

	private MockMvcRequestBuilder prices() {
		return mvc.get().uri("/api/noncovered/prices").param("itemCd", MRI).param("lat", "37.5").param("lng", "127.0")
				.param("radius", "500");
	}

	private long insertSnapshot(String source) {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count) values (?, 'ACTIVE', '2026-10-06', 0)
				""", source);
		return jdbcTemplate.queryForObject("select max(id) from snapshot", Long.class);
	}

	private void insertHospital(long snapshotId, String ykiho, double lat, double lng) {
		jdbcTemplate.update("""
				insert into hospital (snapshot_id, ykiho, name, cl_cd, cl_cd_nm, latitude, longitude, sido_cd)
				values (?, ?, ?, '21', '병원', ?, ?, '110000')
				""", snapshotId, ykiho, ykiho + "병원", lat, lng);
	}

	private void insertPrice(long snapshotId, String ykiho, long min, long max) {
		jdbcTemplate.update("""
				insert into nonpay_price (snapshot_id, npay_cd, ykiho, min_price, max_price, sido_cd)
				values (?, ?, ?, ?, ?, '110000')
				""", snapshotId, MRI, ykiho, min, max);
	}

	/** 한 요일만 09:00~18:00 */
	private static HospitalDetailRecord openOn(String ykiho, DayOfWeek day) {
		return new HospitalDetailRecord(ykiho, new WeeklyHours(Map.of(day, new OpenRange(540, 1080)), Set.of()), null,
				null, null, null, null, null, null, null, null, null, null);
	}

}
