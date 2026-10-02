package dev.chhun.hospitalcompare.collector.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.lessThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.hospital.entity.SnapshotStatus;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 한 페이지가 실패하면 진행 중인 나머지 페이지를 취소하고 더 호출하지 않는지 본다. 회귀하면 이미 FAILED인 실행이
 * 남은 페이지를 계속 호출해 일 한도를 쓰고 종료도 늦어진다. 이 동작은 라이브러리 세부 셋이 맞물려야 성립한다:
 * interrupt가 재시도 가능한 I/O 예외가 되고, 재시도 백오프 sleep이 interrupt로 바로 깨고, 관문이 그 예외를 그대로 던진다.
 * 페이지 크기(numOfRows)를 2로 해 10건을 5페이지로 나누고, 읽기 제한을 지연(3초)보다 길게 둔다.
 */
@SpringBootTest(properties = {
		"hira.service-key=fake+Key/For==Test",
		"hira.max-concurrency=4",
		"hira.requests-per-second=1000",
		"hira.read-timeout=5s",
		"hira.retry.initial-delay=5ms",
		"hira.retry.max-delay=20ms",
		"collector.num-of-rows=2",
		"collector.run-on-startup=false"})
@ActiveProfiles("collector")
@Import(TestcontainersConfiguration.class)
class HospitalCollectorCancellationTest {

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

	@Test
	void 한_페이지가_일_한도로_실패하면_진행_중인_나머지_페이지를_취소하고_더_호출하지_않는다() throws IOException {
		stubPage(1, "hosp-basis-list-total10.xml", 200, 0);
		// 2페이지는 조금 늦게 실패시켜, 3~5페이지 요청이 모두 나가 응답을 기다리는 중일 때 실패가 닿게 한다.
		stubPage(2, "gateway-error-22.xml", 403, 300);
		for (int pageNo = 3; pageNo <= 5; pageNo++) {
			stubPage(pageNo, "hosp-basis-list-total10.xml", 200, 3000);
		}

		long started = System.nanoTime();
		CollectResult result = collector.collect(null);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

		assertThat(result.status()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(result.failureReason()).contains("22");
		// 취소하지 않으면 3~5페이지의 지연(3초)이 끝나기를 기다리므로 3초를 넘는다.
		assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
		for (int pageNo = 3; pageNo <= 5; pageNo++) {
			wireMock.verify(lessThanOrExactly(1), getRequestedFor(urlPathEqualTo(PATH))
					.withQueryParam("pageNo", equalTo(String.valueOf(pageNo))));
		}
	}

	private static void stubPage(int pageNo, String fixture, int status, int delayMillis) throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/" + fixture).getContentAsByteArray();
		wireMock.stubFor(get(urlPathEqualTo(PATH))
				.withQueryParam("pageNo", equalTo(String.valueOf(pageNo)))
				.willReturn(aResponse()
						.withStatus(status)
						.withFixedDelay(delayMillis)
						.withHeader("Content-Type", "application/xml;charset=UTF-8")
						.withBody(body)));
	}

}
