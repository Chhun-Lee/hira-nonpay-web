package dev.chhun.hospitalcompare.collector.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.hospital.entity.Snapshot;
import dev.chhun.hospitalcompare.hospital.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.hospital.repository.SnapshotRepository;
import java.io.IOException;
import java.sql.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
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
 * collector 프로파일로 뜨면 --sgguCd 시군구를 수집한다. 심평원 응답은 픽스처(WireMock), DB는 Testcontainers MySQL.
 * 픽스처는 totalCount 3건을 numOfRows 2로 나눈 두 페이지다.
 */
@SpringBootTest(args = "--sgguCd=110001",
		properties = {"hira.service-key=fake+Key/For==Test", "collector.num-of-rows=2"})
@ActiveProfiles("collector")
@Import(TestcontainersConfiguration.class)
class HospitalCollectorTest {

	private static final String PATH = "/hospInfoServicev2/getHospBasisList";

	// 컨텍스트가 뜰 때 러너가 이미 호출하므로 테스트마다 스텁을 지우지 않는다.
	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.resetOnEachTest(false)
			.build();

	@DynamicPropertySource
	static void hiraBaseUrl(DynamicPropertyRegistry registry) {
		registry.add("hira.base-url", wireMock::baseUrl);
	}

	@BeforeAll
	static void stubPages() throws IOException {
		stubPage(1, "hosp-basis-list-page1.xml");
		stubPage(2, "hosp-basis-list-page2.xml");
	}

	@Autowired
	HospitalCollector collector;

	@Autowired
	SnapshotRepository snapshotRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void collector로_뜨면_인자로_받은_시군구를_마지막_페이지까지_적재한다() {
		wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
				.withQueryParam("sgguCd", equalTo("110001"))
				.withQueryParam("pageNo", equalTo("1")));
		wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
				.withQueryParam("sgguCd", equalTo("110001"))
				.withQueryParam("pageNo", equalTo("2")));

		assertThat(hospitalCount()).isEqualTo(3);
		Snapshot active = snapshotRepository.findByStatus(SnapshotStatus.ACTIVE).orElseThrow();
		assertThat(active.getRecordCount()).isEqualTo(3);
	}

	@Test
	void 같은_시군구를_다시_수집해도_건수가_같다() {
		CollectResult result = collector.collect("110001");

		assertThat(result.calls()).isEqualTo(2);
		assertThat(result.received()).isEqualTo(3);
		assertThat(result.snapshotRecordCount()).isEqualTo(3);
		assertThat(hospitalCount()).isEqualTo(3);
		assertThat(snapshotRepository.findAll()).hasSize(1);
	}

	@Test
	void 응답_필드를_컬럼으로_옮긴다() {
		Map<String, Object> hospital = jdbcTemplate.queryForMap(
				"select * from hospital where name = '가상종합병원'");
		assertThat(hospital)
				.containsEntry("cl_cd", "11")
				.containsEntry("cl_cd_nm", "종합병원")
				.containsEntry("sido_cd_nm", "서울")
				.containsEntry("sggu_cd", "110001")
				.containsEntry("emdong_nm", "역삼동")
				.containsEntry("address", "서울특별시 강남구 가상로 101 (역삼동)")
				.containsEntry("phone", "02-0000-0001")
				.containsEntry("established_date", Date.valueOf("1995-03-01"))
				.containsEntry("latitude", 37.5006123456789)
				.containsEntry("longitude", 127.0365123456789)
				.containsEntry("doctor_count", 152);

		Map<String, Object> noCoordinates = jdbcTemplate.queryForMap(
				"select latitude, longitude from hospital where name = '가상치과의원'");
		assertThat(noCoordinates).containsEntry("latitude", null).containsEntry("longitude", null);
	}

	private long hospitalCount() {
		return jdbcTemplate.queryForObject("select count(*) from hospital", Long.class);
	}

	private static void stubPage(int pageNo, String fixture) throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/" + fixture).getContentAsByteArray();
		wireMock.stubFor(get(urlPathEqualTo(PATH))
				.withQueryParam("pageNo", equalTo(String.valueOf(pageNo)))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/xml;charset=UTF-8")
						.withBody(body)));
	}

}
