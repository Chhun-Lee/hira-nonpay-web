package dev.chhun.hospitalcompare.collector.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.collector.dto.NonpayCollectResult;
import dev.chhun.hospitalcompare.nonpay.repository.NonpayPriceRepository;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.repository.QualityIssueRepository;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 정보용 집계·정리가 실패해도 게이트를 통과한 수집은 ACTIVE로 끝나야 한다. 스파이가 다른 테스트에 새지 않게 별도 클래스로 둔다.
 * 픽스처(항목 2개, 가격 행 5개 중 1개 제외)는 NonpayCollectorTest와 같다.
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
class NonpayCollectorResilienceTest {

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

	@MockitoSpyBean
	NonpayPriceRepository priceRepository;

	@MockitoSpyBean
	SnapshotService snapshotService;

	@MockitoSpyBean
	QualityIssueRepository qualityIssueRepository;

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
	void 병원_집계가_실패해도_ACTIVE로_바꾸고_집계는_null이다() throws IOException {
		insertHospitalList("Y-HOSP-A");
		stubAllPrices();
		doThrow(new IllegalStateException("집계 실패")).when(priceRepository).countUnmatchedYkiho(anyLong(), anyLong());

		NonpayCollectResult result = collector.collect(List.of());

		assertThat(result.status()).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(result.unmatchedYkiho()).isNull();
		assertThat(snapshotService.activeRecordCount(SnapshotSource.NONPAY)).contains(4);
	}

	@Test
	void 정리가_실패해도_결과는_ACTIVE이고_삭제_건수는_0이다() throws IOException {
		stubAllPrices();
		doThrow(new IllegalStateException("정리 실패")).when(snapshotService).cleanup(SnapshotSource.NONPAY);

		NonpayCollectResult result = collector.collect(List.of());

		assertThat(result.status()).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(result.deleted()).isZero();
	}

	@Test
	void 품질_이슈_집계가_실패해도_결과는_ACTIVE이고_이슈는_비어_있다() throws IOException {
		stubAllPrices();
		doThrow(new IllegalStateException("집계 실패")).when(qualityIssueRepository).countBySnapshot(anyLong());

		NonpayCollectResult result = collector.collect(List.of());

		assertThat(result.status()).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(result.issues()).isEmpty();
	}

	private void stubAllPrices() throws IOException {
		stubPrices("ABZ010001", 1, "nonpay/hosp-list-ABZ010001-p1.xml", 200, 0);
		stubPrices("ABZ010001", 2, "nonpay/hosp-list-ABZ010001-p2.xml", 200, 0);
		stubPrices("HE1180000", 1, "nonpay/hosp-list-HE1180000.xml", 200, 0);
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
