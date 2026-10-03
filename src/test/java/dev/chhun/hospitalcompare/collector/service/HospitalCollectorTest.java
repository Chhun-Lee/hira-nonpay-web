package dev.chhun.hospitalcompare.collector.service;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotService;
import java.io.IOException;
import java.util.List;
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

/**
 * 심평원 응답은 픽스처(WireMock), DB는 Testcontainers MySQL. 러너는 띄우지 않고 collect를 직접 부른다.
 * 기본 픽스처는 totalCount 3건을 2건씩 나눈 두 페이지다. numOfRows를 100으로 요청하면 1페이지가 2건만 와서
 * 수집기가 페이지 크기를 2로 줄여 나머지를 요청한다.
 */
@SpringBootTest(properties = {
		"hira.service-key=fake+Key/For==Test",
		"hira.requests-per-second=1000",
		"hira.read-timeout=2s",
		"hira.retry.initial-delay=5ms",
		"hira.retry.max-delay=20ms",
		"collector.num-of-rows=100",
		"collector.run-on-startup=false"})
@ActiveProfiles("collector")
@Import(TestcontainersConfiguration.class)
class HospitalCollectorTest {

	private static final String PATH = "/hospInfoServicev2/getHospBasisList";

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@DynamicPropertySource
	static void hiraBaseUrl(DynamicPropertyRegistry registry) {
		registry.add("hira.base-url", wireMock::baseUrl);
	}

	@Autowired
	HospitalCollector collector;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.update("delete from hospital");
		jdbcTemplate.update("delete from data_quality_issue");
		jdbcTemplate.update("delete from snapshot");
	}

	@Test
	void 전국_목록을_받아_검증을_통과하면_화면용으로_바꾼다() throws IOException {
		stubPage(1, "hosp-basis-list-page1.xml");
		stubPage(2, "hosp-basis-list-page2.xml");

		CollectResult result = collector.collect(null);

		assertThat(result.status()).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(result.scope()).isEqualTo("전국");
		assertThat(result.totalCount()).isEqualTo(3);
		assertThat(result.received()).isEqualTo(3);
		assertThat(result.loaded()).isEqualTo(3);
		assertThat(result.calls()).isEqualTo(2);
		assertThat(statuses()).containsExactly("ACTIVE");
		wireMock.verify(2, getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("sgguCd", absent()));
	}

	@Test
	void 페이지_크기를_줄여_주면_그_크기로_나머지를_요청한다() throws IOException {
		stubPage(1, "hosp-basis-list-page1.xml");
		stubPage(2, "hosp-basis-list-page2.xml");

		collector.collect(null);

		wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
				.withQueryParam("pageNo", equalTo("1")).withQueryParam("numOfRows", equalTo("100")));
		wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
				.withQueryParam("pageNo", equalTo("2")).withQueryParam("numOfRows", equalTo("2")));
	}

	@Test
	void 시군구를_주면_그_시군구만_요청한다() throws IOException {
		stubPage(1, "hosp-basis-list-page1.xml");
		stubPage(2, "hosp-basis-list-page2.xml");

		CollectResult result = collector.collect("110001");

		assertThat(result.scope()).isEqualTo("110001");
		wireMock.verify(2, getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("sgguCd", equalTo("110001")));
	}

	@Test
	void 다시_수집하면_이전_화면용은_RETIRED가_되고_그보다_오래된_병원_행은_지운다() throws IOException {
		stubPage(1, "hosp-basis-list-page1.xml");
		stubPage(2, "hosp-basis-list-page2.xml");

		CollectResult first = collector.collect(null);
		CollectResult second = collector.collect(null);
		CollectResult third = collector.collect(null);

		assertThat(statuses()).containsExactly("RETIRED", "RETIRED", "ACTIVE");
		assertThat(rowsOf(first.snapshotId())).isZero();
		assertThat(rowsOf(second.snapshotId())).isEqualTo(3);
		assertThat(rowsOf(third.snapshotId())).isEqualTo(3);
		assertThat(third.deleted()).isEqualTo(3);
	}

	@Test
	void 남아_있던_STAGE는_FAILED로_정리한다() throws IOException {
		stubPage(1, "hosp-basis-list-page1.xml");
		stubPage(2, "hosp-basis-list-page2.xml");
		jdbcTemplate.update("insert into snapshot (base_date, status, record_count) values ('2000-01-01', 'STAGE', 0)");

		collector.collect(null);

		assertThat(jdbcTemplate.queryForObject("select failure_reason from snapshot where base_date = '2000-01-01'",
				String.class)).isEqualTo(SnapshotService.INTERRUPTED);
	}

	@Test
	void 품질_이슈를_남기고_필수값_누락이_많으면_전환하지_않는다() throws IOException {
		stubPage(1, "hosp-basis-list-with-issues.xml");

		CollectResult result = collector.collect(null);

		assertThat(result.received()).isEqualTo(4);
		assertThat(result.loaded()).isEqualTo(3);
		assertThat(result.issues())
				.containsEntry(QualityIssueType.MISSING_REQUIRED, 1)
				.containsEntry(QualityIssueType.COORDINATES_OUT_OF_RANGE, 1)
				.containsEntry(QualityIssueType.MISSING_COORDINATES, 1)
				.containsEntry(QualityIssueType.INVALID_DATE, 1);
		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.failureReason()).contains("필수값");
		assertThat(jdbcTemplate.queryForObject("select count(*) from data_quality_issue", Integer.class)).isEqualTo(4);
	}

	@Test
	void 건수가_급감하면_전환하지_않고_화면용은_그대로다() throws IOException {
		long active = seedActive(10);
		stubPage(1, "hosp-basis-list-page1.xml");
		stubPage(2, "hosp-basis-list-page2.xml");

		CollectResult result = collector.collect(null);

		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.failureReason()).contains("3").contains("10");
		assertThat(activeId()).isEqualTo(active);
		assertThat(rowsOf(active)).isEqualTo(10);
	}

	@Test
	void 일_한도에_걸리면_재시도하지_않고_FAILED로_끝나며_화면용은_그대로다() throws IOException {
		long active = seedActive(3);
		stubPage(1, "hosp-basis-list-page1.xml");
		stubPage(2, "gateway-error-22.xml", 403);

		CollectResult result = collector.collect(null);

		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.failureReason()).contains("22");
		assertThat(activeId()).isEqualTo(active);
		assertThat(rowsOf(active)).isEqualTo(3);
		wireMock.verify(1, getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("pageNo", equalTo("2")));
	}

	@Test
	void 일시_오류는_재시도해서_넘긴다() throws IOException {
		stubPage(1, "hosp-basis-list-page1.xml");
		byte[] page2 = new ClassPathResource("fixtures/hira/hosp-basis-list-page2.xml").getContentAsByteArray();
		wireMock.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("pageNo", equalTo("2"))
				.inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
				.willReturn(serviceUnavailable()).willSetStateTo("recovered"));
		wireMock.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("pageNo", equalTo("2"))
				.inScenario("retry").whenScenarioStateIs("recovered")
				.willReturn(aResponse().withHeader("Content-Type", "application/xml;charset=UTF-8").withBody(page2)));

		CollectResult result = collector.collect(null);

		assertThat(result.status()).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(result.retries()).isEqualTo(1);
		assertThat(result.loaded()).isEqualTo(3);
	}

	private long seedActive(int hospitals) {
		jdbcTemplate.update("insert into snapshot (base_date, status, record_count) values ('2026-09-25', 'ACTIVE', ?)",
				hospitals);
		long snapshotId = activeId();
		for (int i = 0; i < hospitals; i++) {
			jdbcTemplate.update("insert into hospital (snapshot_id, ykiho, name) values (?, ?, '기존병원')",
					snapshotId, "SEED-" + i);
		}
		return snapshotId;
	}

	private long activeId() {
		return jdbcTemplate.queryForObject("select id from snapshot where status = 'ACTIVE'", Long.class);
	}

	private List<String> statuses() {
		return jdbcTemplate.queryForList("select status from snapshot order by id", String.class);
	}

	private int rowsOf(long snapshotId) {
		return jdbcTemplate.queryForObject("select count(*) from hospital where snapshot_id = ?", Integer.class, snapshotId);
	}

	private static void stubPage(int pageNo, String fixture) throws IOException {
		stubPage(pageNo, fixture, 200);
	}

	private static void stubPage(int pageNo, String fixture, int status) throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/" + fixture).getContentAsByteArray();
		wireMock.stubFor(get(urlPathEqualTo(PATH))
				.withQueryParam("pageNo", equalTo(String.valueOf(pageNo)))
				.willReturn(aResponse()
						.withStatus(status)
						.withHeader("Content-Type", "application/xml;charset=UTF-8")
						.withBody(body)));
	}

}
