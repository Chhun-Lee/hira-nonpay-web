package dev.chhun.hospitalcompare.collector.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.collector.dto.NonpayCollectResult;
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
 * 항목 2개(ABZ010001 3곳 = 2페이지, HE1180000 2곳 중 1곳은 최소 > 최대)와 통계로 흐름 전체를 본다.
 * 픽스처에 일부러 넣은 이상 행(5행 중 1행 제외) 때문에 제외 비율 허용치를 50%로 둔다.
 */
@SpringBootTest(properties = {
		"hira.service-key=fake+Key/For==Test",
		"hira.nonpay.requests-per-second=1000",
		"hira.nonpay.read-timeout=5s",
		"hira.retry.initial-delay=5ms",
		"hira.retry.max-delay=20ms",
		"collector.nonpay.num-of-rows=2",
		"collector.nonpay.progress-interval=0s",
		"collector.nonpay.gate.max-invalid-ratio=0.5",
		"collector.run-on-startup=false"})
@ActiveProfiles("collector")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class NonpayCollectorTest {

	private static final String BASE = "/nonPaymentDamtInfoService/";

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@DynamicPropertySource
	static void hiraBaseUrl(DynamicPropertyRegistry registry) {
		registry.add("hira.base-url", wireMock::baseUrl);
	}

	@Autowired
	NonpayCollector collector;

	@Autowired
	SnapshotService snapshotService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() throws IOException {
		for (String table : List.of("nonpay_price", "nonpay_item", "nonpay_stat", "data_quality_issue", "hospital",
				"snapshot")) {
			jdbcTemplate.update("delete from " + table);
		}
		wireMock.resetAll();
		stub("getNonPaymentItemCodeList2", Map.of(), "nonpay/item-codes.xml", 200, 0);
		stub("getNonPaymentItemClcdList", Map.of(), "nonpay/stat-clcd.xml", 200, 0);
		stub("getNonPaymentItemSidoCdList", Map.of(), "nonpay/stat-sido.xml", 200, 0);
	}

	@Test
	void 전_항목을_받아_검증을_통과하면_ACTIVE로_바꾼다(CapturedOutput output) throws IOException {
		long hospitalSnapshot = insertHospitalList("Y-HOSP-A", "Y-HOSP-B");
		stubPrices("ABZ010001", 1, "nonpay/hosp-list-ABZ010001-p1.xml", 200, 0);
		stubPrices("ABZ010001", 2, "nonpay/hosp-list-ABZ010001-p2.xml", 200, 0);
		stubPrices("HE1180000", 1, "nonpay/hosp-list-HE1180000.xml", 200, 0);

		NonpayCollectResult result = collector.collect(List.of());

		assertThat(result.status()).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(result.trial()).isFalse();
		assertThat(result.targetItems()).isEqualTo(2);
		assertThat(result.itemCodes()).isEqualTo(2);
		assertThat(result.statRows()).isEqualTo(12);
		assertThat(result.received()).isEqualTo(5);
		assertThat(result.excluded()).isEqualTo(1);
		assertThat(result.loaded()).isEqualTo(4);
		assertThat(result.emptyItems()).isZero();
		assertThat(result.unmatchedYkiho()).isEqualTo(1L);
		assertThat(result.calls()).isEqualTo(6);
		assertThat(result.issues())
				.containsEntry(QualityIssueType.INVALID_PRICE, 2)
				.containsEntry(QualityIssueType.INVALID_DATE, 1)
				.containsEntry(QualityIssueType.UNKNOWN_STAT_KEY, 1);
		assertThat(snapshotService.activeRecordCount(SnapshotSource.NONPAY)).contains(4);
		assertThat(snapshotService.activeSnapshotId(SnapshotSource.HOSPITAL_LIST)).contains(hospitalSnapshot);
		assertThat(jdbcTemplate.queryForObject(
				"select min_price from nonpay_price where npay_cd = 'ABZ010001' and ykiho = 'Y-HOSP-A'", Long.class))
				.isEqualTo(285000L);
		assertThat(output).contains("진행 1차");
	}

	@Test
	void 시험_실행은_첫_실행이어도_ACTIVE가_되지_않는다() throws IOException {
		stubPrices("ABZ010001", 1, "nonpay/hosp-list-ABZ010001-p1.xml", 200, 0);
		stubPrices("ABZ010001", 2, "nonpay/hosp-list-ABZ010001-p2.xml", 200, 0);

		NonpayCollectResult result = collector.collect(List.of("ABZ010001"));

		assertThat(result.status()).isEqualTo(SnapshotStatus.TRIAL);
		assertThat(result.succeeded()).isTrue();
		assertThat(result.reason()).contains("시험 실행");
		assertThat(result.loaded()).isEqualTo(3);
		assertThat(snapshotService.activeRecordCount(SnapshotSource.NONPAY)).isEmpty();
		wireMock.verify(0, getRequestedFor(urlPathEqualTo(BASE + "getNonPaymentItemHospList2"))
				.withQueryParam("itemCd", equalTo("HE1180000")));
		// 시험 실행 스냅샷의 행은 정리된다.
		assertThat(jdbcTemplate.queryForObject("select count(*) from nonpay_price", Integer.class)).isZero();
	}

	@Test
	void 통계에_없는_항목을_시험_실행하면_FAILED로_끝난다() {
		NonpayCollectResult result = collector.collect(List.of("ZZ9999999"));

		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.reason()).contains("통계에 없는 항목 코드").contains("ZZ9999999");
	}

	@Test
	void 비급여_수집이_실패해도_병원_목록_ACTIVE와_행은_그대로다() throws IOException {
		long hospitalSnapshot = insertHospitalList("Y-HOSP-A", "Y-HOSP-B");
		wireMock.resetAll();
		stub("getNonPaymentItemCodeList2", Map.of(), "gateway-error-22.xml", 403, 0);

		NonpayCollectResult result = collector.collect(List.of());

		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.reason()).contains("22");
		assertThat(snapshotService.activeSnapshotId(SnapshotSource.HOSPITAL_LIST)).contains(hospitalSnapshot);
		assertThat(jdbcTemplate.queryForObject("select count(*) from hospital where snapshot_id = ?", Integer.class,
				hospitalSnapshot)).isEqualTo(2);
	}

	@Test
	void 항목_하나가_일_한도로_실패하면_진행_중인_나머지를_취소한다() throws IOException {
		// ABZ010001은 조금 늦게 실패시켜, HE1180000 요청이 나가 응답을 기다리는 중일 때 실패가 닿게 한다.
		stubPrices("ABZ010001", 1, "gateway-error-22.xml", 403, 300);
		stubPrices("HE1180000", 1, "nonpay/hosp-list-HE1180000.xml", 200, 3000);

		long started = System.nanoTime();
		NonpayCollectResult result = collector.collect(List.of());
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.reason()).contains("22");
		// 취소하지 않으면 HE1180000의 지연(3초)이 끝나기를 기다리므로 3초를 넘는다.
		assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
		wireMock.verify(exactly(1), getRequestedFor(urlPathEqualTo(BASE + "getNonPaymentItemHospList2"))
				.withQueryParam("itemCd", equalTo("HE1180000")));
	}

	@Test
	void 이전_ACTIVE보다_크게_줄면_FAILED로_남기고_이전_ACTIVE를_유지한다() throws IOException {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count)
				values ('NONPAY', 'ACTIVE', '2026-09-01', 100)
				""");
		long previous = jdbcTemplate.queryForObject("select id from snapshot where source = 'NONPAY'", Long.class);
		stubPrices("ABZ010001", 1, "nonpay/hosp-list-ABZ010001-p1.xml", 200, 0);
		stubPrices("ABZ010001", 2, "nonpay/hosp-list-ABZ010001-p2.xml", 200, 0);
		stubPrices("HE1180000", 1, "nonpay/hosp-list-HE1180000.xml", 200, 0);

		NonpayCollectResult result = collector.collect(List.of());

		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.reason()).contains("부분 수집");
		assertThat(snapshotService.activeSnapshotId(SnapshotSource.NONPAY)).contains(previous);
	}

	@Test
	void 시험_실행은_이전_ACTIVE_건수와_비교하지_않고_TRIAL로_끝난다() throws IOException {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count)
				values ('NONPAY', 'ACTIVE', '2026-09-01', 100)
				""");
		long previous = jdbcTemplate.queryForObject("select id from snapshot where source = 'NONPAY'", Long.class);
		stubPrices("ABZ010001", 1, "nonpay/hosp-list-ABZ010001-p1.xml", 200, 0);
		stubPrices("ABZ010001", 2, "nonpay/hosp-list-ABZ010001-p2.xml", 200, 0);

		NonpayCollectResult result = collector.collect(List.of("ABZ010001"));

		assertThat(result.status()).isEqualTo(SnapshotStatus.TRIAL);
		assertThat(result.reason()).doesNotContain("부분 수집");
		assertThat(snapshotService.activeSnapshotId(SnapshotSource.NONPAY)).contains(previous);
	}

	private long insertHospitalList(String... ykihos) {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count)
				values ('HOSPITAL_LIST', 'ACTIVE', '2026-10-03', ?)
				""", ykihos.length);
		long snapshotId = jdbcTemplate.queryForObject(
				"select id from snapshot where source = 'HOSPITAL_LIST' and status = 'ACTIVE'", Long.class);
		for (String ykiho : ykihos) {
			jdbcTemplate.update("insert into hospital (snapshot_id, ykiho, name) values (?, ?, '가상병원')",
					snapshotId, ykiho);
		}
		return snapshotId;
	}

	private static void stubPrices(String itemCd, int pageNo, String fixture, int status, int delayMillis)
			throws IOException {
		stub("getNonPaymentItemHospList2", Map.of("itemCd", itemCd, "pageNo", String.valueOf(pageNo)), fixture,
				status, delayMillis);
	}

	private static void stub(String operation, Map<String, String> query, String fixture, int status, int delayMillis)
			throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/" + fixture).getContentAsByteArray();
		var mapping = get(urlPathEqualTo(BASE + operation));
		for (Map.Entry<String, String> entry : query.entrySet()) {
			mapping = mapping.withQueryParam(entry.getKey(), equalTo(entry.getValue()));
		}
		wireMock.stubFor(mapping.willReturn(aResponse()
				.withStatus(status)
				.withFixedDelay(delayMillis)
				.withHeader("Content-Type", "application/xml;charset=UTF-8")
				.withBody(body)));
	}

}
