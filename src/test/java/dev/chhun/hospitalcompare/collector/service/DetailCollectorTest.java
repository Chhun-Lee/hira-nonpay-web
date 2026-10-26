package dev.chhun.hospitalcompare.collector.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.collector.dto.DetailCollectResult;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotService;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 심평원 응답은 픽스처(WireMock), DB는 Testcontainers MySQL. 러너는 띄우지 않고 collect를 직접 부른다.
 * 병원 목록: Y-KIM(병원, 110001), Y-ODD(요양병원, 110001), Y-EMPTY(종합병원, 110002), Y-CLINIC(의원, 대상 아님).
 * Y-ODD는 일부러 이상값을 넣은 응답이라 진료 시각 해석 실패 허용치를 50%로 둔다.
 */
@SpringBootTest(properties = {
		"hira.service-key=fake+Key/For==Test",
		"hira.detail.requests-per-second=1000",
		"hira.detail.read-timeout=5s",
		"hira.retry.initial-delay=5ms",
		"hira.retry.max-delay=20ms",
		"collector.detail.num-of-rows=2",
		"collector.detail.progress-interval=0s",
		"collector.detail.gate.max-invalid-hours-ratio=0.5",
		"collector.run-on-startup=false"})
@ActiveProfiles("collector")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class DetailCollectorTest {

	private static final String BASE = "/MadmDtlInfoService2.8/";

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@DynamicPropertySource
	static void hiraBaseUrl(DynamicPropertyRegistry registry) {
		registry.add("hira.base-url", wireMock::baseUrl);
	}

	@Autowired
	DetailCollector collector;

	@Autowired
	SnapshotService snapshotService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		for (String table : List.of("hospital_detail", "hospital_subject", "data_quality_issue", "hospital",
				"snapshot")) {
			jdbcTemplate.update("delete from " + table);
		}
		wireMock.resetAll();
	}

	@Test
	void 병원급만_받아_검증을_통과하면_ACTIVE로_바꾼다(CapturedOutput output) throws IOException {
		insertHospitalList();
		stubAll(0);

		DetailCollectResult result = collector.collect(null);

		assertThat(result.status()).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(result.trial()).isFalse();
		assertThat(result.targets()).isEqualTo(3);
		// 세부정보 3 + 진료과목(Y-KIM 1, Y-ODD 2페이지, Y-EMPTY 1)
		assertThat(result.calls()).isEqualTo(7);
		assertThat(result.detailRows()).isEqualTo(2);
		assertThat(result.emptyDetails()).isEqualTo(1);
		assertThat(result.subjectRows()).isEqualTo(13);
		assertThat(result.withoutSubjects()).isZero();
		// Y-KIM 14칸 + Y-ODD 11칸(월~금 10 + 토 시작만 1). 읽지 못한 칸: 수 2 + 토 1
		assertThat(result.hourFields()).isEqualTo(25);
		assertThat(result.invalidHourFields()).isEqualTo(3);
		assertThat(result.coverage()).containsExactly(
				Map.entry("11", new DetailCollectResult.Coverage(1, 0)),
				Map.entry("21", new DetailCollectResult.Coverage(1, 1)),
				Map.entry("28", new DetailCollectResult.Coverage(1, 1)));
		assertThat(result.issues())
				.containsEntry(QualityIssueType.HOURS_INVALID, 2)
				.containsEntry(QualityIssueType.LUNCH_UNPARSED, 1)
				.containsEntry(QualityIssueType.CLOSED_NOTE_UNCLASSIFIED, 1)
				.containsEntry(QualityIssueType.INVALID_COUNT, 1);
		assertThat(snapshotService.activeRecordCount(SnapshotSource.HOSPITAL_DETAIL)).contains(2);
		assertThat(jdbcTemplate.queryForObject(
				"select mon_open from hospital_detail where ykiho = 'Y-KIM'", Integer.class)).isEqualTo(510);
		assertThat(jdbcTemplate.queryForObject(
				"select hours_unknown_days from hospital_detail where ykiho = 'Y-ODD'", String.class))
				.isEqualTo("WEDNESDAY,SATURDAY");
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from hospital_subject where ykiho = 'Y-ODD'", Integer.class)).isEqualTo(3);
		wireMock.verify(0, getRequestedFor(urlPathEqualTo(BASE + "getDtlInfo2.8"))
				.withQueryParam("ykiho", equalTo("Y-CLINIC")));
		assertThat(output).contains("진행");
	}

	@Test
	void 시군구를_주면_그_시군구_병원급만_시험_실행하고_ACTIVE가_되지_않는다() throws IOException {
		insertHospitalList();
		stubAll(0);

		DetailCollectResult result = collector.collect("110002");

		assertThat(result.status()).isEqualTo(SnapshotStatus.TRIAL);
		assertThat(result.succeeded()).isTrue();
		assertThat(result.reason()).contains("시험 실행");
		assertThat(result.targets()).isEqualTo(1);
		assertThat(snapshotService.activeSnapshotId(SnapshotSource.HOSPITAL_DETAIL)).isEmpty();
		wireMock.verify(0, getRequestedFor(urlPathEqualTo(BASE + "getDtlInfo2.8"))
				.withQueryParam("ykiho", equalTo("Y-KIM")));
		// 시험 실행 스냅샷의 행은 정리된다.
		assertThat(jdbcTemplate.queryForObject("select count(*) from hospital_subject", Integer.class)).isZero();
	}

	@Test
	void 기관_하나가_일_한도로_실패하면_진행_중인_나머지를_취소하고_FAILED다() throws IOException {
		insertHospitalList();
		stubAll(3000);
		// Y-KIM 세부정보만 조금 늦게 실패시켜, 나머지 요청이 응답을 기다리는 중일 때 실패가 닿게 한다.
		stub("getDtlInfo2.8", "Y-KIM", null, "gateway-error-22.xml", 403, 300);

		long started = System.nanoTime();
		DetailCollectResult result = collector.collect(null);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.reason()).contains("22");
		// 취소하지 않으면 다른 요청의 지연(3초)이 끝나기를 기다리므로 3초를 넘는다.
		assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
		assertThat(snapshotService.activeSnapshotId(SnapshotSource.HOSPITAL_DETAIL)).isEmpty();
	}

	@Test
	void 세부정보_기관이_이전_ACTIVE보다_크게_줄면_FAILED로_남기고_이전_ACTIVE를_유지한다() throws IOException {
		insertHospitalList();
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count)
				values ('HOSPITAL_DETAIL', 'ACTIVE', '2026-10-01', 10)
				""");
		long previous = snapshotService.activeSnapshotId(SnapshotSource.HOSPITAL_DETAIL).orElseThrow();
		stubAll(0);

		DetailCollectResult result = collector.collect(null);

		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.reason()).contains("부분 수집 의심");
		assertThat(snapshotService.activeSnapshotId(SnapshotSource.HOSPITAL_DETAIL)).contains(previous);
	}

	@Test
	void 병원_목록_ACTIVE가_없으면_대상을_정할_수_없어_예외다() {
		assertThatThrownBy(() -> collector.collect(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("병원 목록 ACTIVE");
	}

	private void insertHospitalList() {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count)
				values ('HOSPITAL_LIST', 'ACTIVE', '2026-10-02', 4)
				""");
		long snapshotId = jdbcTemplate.queryForObject(
				"select id from snapshot where source = 'HOSPITAL_LIST' and status = 'ACTIVE'", Long.class);
		insertHospital(snapshotId, "Y-KIM", "21", "110001");
		insertHospital(snapshotId, "Y-ODD", "28", "110001");
		insertHospital(snapshotId, "Y-EMPTY", "11", "110002");
		insertHospital(snapshotId, "Y-CLINIC", "31", "110001");
	}

	private void insertHospital(long snapshotId, String ykiho, String clCd, String sgguCd) {
		jdbcTemplate.update("insert into hospital (snapshot_id, ykiho, name, cl_cd, sggu_cd) values (?, ?, '가상병원', ?, ?)",
				snapshotId, ykiho, clCd, sgguCd);
	}

	private static void stubAll(int delayMillis) throws IOException {
		stub("getDtlInfo2.8", "Y-KIM", null, "detail/dtl-info.xml", 200, delayMillis);
		stub("getDtlInfo2.8", "Y-ODD", null, "detail/dtl-info-odd.xml", 200, delayMillis);
		stub("getDtlInfo2.8", "Y-EMPTY", null, "detail/dtl-info-empty.xml", 200, delayMillis);
		stub("getDgsbjtInfo2.8", "Y-KIM", null, "detail/subjects.xml", 200, delayMillis);
		stub("getDgsbjtInfo2.8", "Y-ODD", 1, "detail/subjects-p1.xml", 200, delayMillis);
		stub("getDgsbjtInfo2.8", "Y-ODD", 2, "detail/subjects-p2.xml", 200, delayMillis);
		stub("getDgsbjtInfo2.8", "Y-EMPTY", null, "detail/subjects.xml", 200, delayMillis);
	}

	private static void stub(String operation, String ykiho, Integer pageNo, String fixture, int status,
			int delayMillis) throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/" + fixture).getContentAsByteArray();
		var mapping = get(urlPathEqualTo(BASE + operation)).withQueryParam("ykiho", equalTo(ykiho));
		if (pageNo != null) {
			mapping = mapping.withQueryParam("pageNo", equalTo(String.valueOf(pageNo)));
		}
		wireMock.stubFor(mapping.willReturn(aResponse()
				.withStatus(status)
				.withFixedDelay(delayMillis)
				.withHeader("Content-Type", "application/xml;charset=UTF-8")
				.withBody(body)));
	}

}
