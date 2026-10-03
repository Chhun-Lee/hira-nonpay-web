package dev.chhun.hospitalcompare.hira.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.NonpayHospPrice;
import dev.chhun.hospitalcompare.hira.dto.NonpayItemCode;
import dev.chhun.hospitalcompare.hira.dto.NonpayPage;
import dev.chhun.hospitalcompare.hira.exception.HiraGatewayException;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

/**
 * 실제 API는 호출하지 않는다. 응답은 src/test/resources/fixtures/hira/nonpay/ 픽스처로 WireMock이 돌려준다.
 */
class NonpayClientTest {

	private static final String SERVICE_KEY = "fake+Key/For==Test";
	private static final String BASE = "/nonPaymentDamtInfoService/";
	private static final HiraProperties.Retry FAST_RETRY =
			new HiraProperties.Retry(3, Duration.ofMillis(5), Duration.ofMillis(20));

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	private NonpayClient client;

	@BeforeEach
	void setUp() {
		client = new NonpayClient(RestClient.builder(), properties(new HiraProperties.HospInfo(Duration.ofSeconds(1), 4, 1000),
				new HiraProperties.Nonpay(Duration.ofSeconds(1), 4, 1000)));
	}

	@Test
	void 항목_코드를_읽는다() throws IOException {
		stub("getNonPaymentItemCodeList2", Map.of("pageNo", "1"), "nonpay/item-codes.xml", 200, 0);

		NonpayPage<NonpayItemCode> page = client.getItemCodes(1, 1000);

		assertThat(page.totalCount()).isEqualTo(2);
		NonpayItemCode first = page.items().getFirst();
		assertThat(first.npayCd()).isEqualTo("ABZ010001");
		assertThat(first.npayKorNm()).isEqualTo("상급병실료/1인실");
		assertThat(first.npayMdivCd()).isEqualTo("1010A");
		assertThat(first.npaySdivCdNm()).isEqualTo("1인실");
		assertThat(first.adtFrDd()).isEqualTo("20180402");
		assertThat(first.adtEndDd()).isEqualTo("99991231");
	}

	@Test
	void 항목별_병원_가격을_읽고_항목코드와_서비스키를_보낸다() throws IOException {
		stub("getNonPaymentItemHospList2", Map.of("itemCd", "ABZ010001", "pageNo", "1"),
				"nonpay/hosp-list-ABZ010001-p1.xml", 200, 0);

		NonpayPage<NonpayHospPrice> page = client.getHospPrices("ABZ010001", 1, 2);

		assertThat(page.totalCount()).isEqualTo(3);
		assertThat(page.items()).extracting(NonpayHospPrice::ykiho).containsExactly("Y-HOSP-A", "Y-HOSP-B");
		NonpayHospPrice first = page.items().getFirst();
		assertThat(first.clCd()).isEqualTo("01");
		assertThat(first.sidoCd()).isEqualTo("110000");
		assertThat(first.sgguCd()).isEqualTo("110001");
		assertThat(first.minPrc()).isEqualTo("285000");
		assertThat(first.maxPrc()).isEqualTo("355000");
		assertThat(first.adtFrDd()).isEqualTo("20260903");
		wireMock.verify(getRequestedFor(urlPathEqualTo(BASE + "getNonPaymentItemHospList2"))
				.withQueryParam("ServiceKey", equalTo(SERVICE_KEY))
				.withQueryParam("itemCd", equalTo("ABZ010001"))
				.withQueryParam("numOfRows", equalTo("2")));
	}

	@Test
	void 병원이_없는_항목은_빈_페이지다() throws IOException {
		stub("getNonPaymentItemHospList2", Map.of("itemCd", "ZZ0000000"), "nonpay/hosp-list-empty.xml", 200, 0);

		NonpayPage<NonpayHospPrice> page = client.getHospPrices("ZZ0000000", 1, 2);

		assertThat(page.items()).isEmpty();
		assertThat(page.totalCount()).isZero();
	}

	@Test
	void 통계는_항목마다_칸_이름과_값의_맵으로_읽는다() throws IOException {
		stub("getNonPaymentItemClcdList", Map.of("pageNo", "1"), "nonpay/stat-clcd.xml", 200, 0);
		stub("getNonPaymentItemSidoCdList", Map.of("pageNo", "1"), "nonpay/stat-sido.xml", 200, 0);

		NonpayPage<Map<String, String>> type = client.getTypeStats(1, 1000);
		NonpayPage<Map<String, String>> sido = client.getSidoStats(1, 1000);

		assertThat(type.totalCount()).isEqualTo(2);
		assertThat(type.items().getFirst())
				.containsEntry("npayCd", "ABZ010001")
				.containsEntry("prcAvgUsgh", "350000")
				.containsEntry("stdDate", "20260903");
		assertThat(sido.items().get(1)).containsEntry("prcAvgPs", "N/A").containsEntry("prcMinXx", "1");
	}

	@Test
	void 게이트웨이_오류는_병원정보와_같은_예외로_분류한다() throws IOException {
		stub("getNonPaymentItemCodeList2", Map.of(), "gateway-error-22.xml", 403, 0);

		assertThatThrownBy(() -> client.getItemCodes(1, 1000))
				.isInstanceOf(HiraGatewayException.class)
				.satisfies(e -> assertThat(((HiraGatewayException) e).isDailyLimitExceeded()).isTrue())
				.hasMessageNotContaining(SERVICE_KEY);
	}

	@Test
	void 비급여_호출이_자리를_다_차지해도_병원정보_호출은_기다리지_않는다() throws Exception {
		HiraProperties oneSlotEach = properties(new HiraProperties.HospInfo(Duration.ofSeconds(5), 1, 1000),
				new HiraProperties.Nonpay(Duration.ofSeconds(5), 1, 1000));
		HiraClient hospClient = new HiraClient(RestClient.builder(), oneSlotEach);
		NonpayClient nonpayClient = new NonpayClient(RestClient.builder(), oneSlotEach);
		stub("getNonPaymentItemCodeList2", Map.of(), "nonpay/item-codes.xml", 200, 2000);
		byte[] hospList = new ClassPathResource("fixtures/hira/hosp-basis-list-page1.xml").getContentAsByteArray();
		wireMock.stubFor(get(urlPathEqualTo("/hospInfoServicev2/getHospBasisList"))
				.willReturn(aResponse().withHeader("Content-Type", "application/xml;charset=UTF-8").withBody(hospList)));

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<?> slowNonpay = executor.submit(() -> nonpayClient.getItemCodes(1, 1000));
			Thread.sleep(300); // 비급여 호출이 비급여 관문의 유일한 자리를 잡을 때까지

			long started = System.nanoTime();
			hospClient.getHospBasisList("110001", 1, 2);
			Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

			// 관문이 하나였다면 비급여 지연(2초)이 끝날 때까지 기다린다.
			assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
			slowNonpay.get();
		}
	}

	private static HiraProperties properties(HiraProperties.HospInfo hospInfo, HiraProperties.Nonpay nonpay) {
		return new HiraProperties(wireMock.baseUrl(), SERVICE_KEY, Duration.ofSeconds(3), FAST_RETRY, hospInfo, nonpay);
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
