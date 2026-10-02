package dev.chhun.hospitalcompare.hira;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

/**
 * 실제 API는 호출하지 않는다. 응답은 전부 src/test/resources/fixtures/hira/ 픽스처로 WireMock이 돌려준다.
 */
class HiraClientTest {

	/** Decoding 키처럼 +, /, = 를 섞은 가짜 키 */
	private static final String SERVICE_KEY = "fake+Key/For==Test";
	private static final String ENCODED_SERVICE_KEY = "fake%2BKey%2FFor%3D%3DTest";
	private static final String PATH = "/hospInfoServicev2/getHospBasisList";

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	private HiraClient client;

	@BeforeEach
	void setUp() {
		client = new HiraClient(RestClient.builder(), new HiraProperties(wireMock.baseUrl(), SERVICE_KEY));
	}

	@Test
	void 목록_응답을_페이지로_파싱한다() throws IOException {
		respondWith("hosp-basis-list-page1.xml");

		HospBasisPage page = client.getHospBasisList("110001", 1, 2);

		assertThat(page.pageNo()).isEqualTo(1);
		assertThat(page.numOfRows()).isEqualTo(2);
		assertThat(page.totalCount()).isEqualTo(3);
		assertThat(page.items()).extracting(HospBasisItem::yadmNm).containsExactly("가상종합병원", "가상내과의원");

		HospBasisItem first = page.items().getFirst();
		assertThat(first.ykiho()).isEqualTo("RklYVFVSRS1ZS0lITy0wMDAxLWdhc2FuZy1qb25naGFwLWJ5ZW9ud29u");
		assertThat(first.clCd()).isEqualTo("11");
		assertThat(first.clCdNm()).isEqualTo("종합병원");
		assertThat(first.sidoCd()).isEqualTo("110000");
		assertThat(first.sgguCd()).isEqualTo("110001");
		assertThat(first.sgguCdNm()).isEqualTo("강남구");
		assertThat(first.emdongNm()).isEqualTo("역삼동");
		assertThat(first.addr()).isEqualTo("서울특별시 강남구 가상로 101 (역삼동)");
		assertThat(first.telno()).isEqualTo("02-0000-0001");
		assertThat(first.estbDd()).isEqualTo("19950301");
		assertThat(first.xPos()).isEqualTo(127.0365123456789);
		assertThat(first.yPos()).isEqualTo(37.5006123456789);
		assertThat(first.drTotCnt()).isEqualTo(152);
	}

	@Test
	void 좌표가_없는_기관은_null로_받는다() throws IOException {
		respondWith("hosp-basis-list-page2.xml");

		HospBasisItem item = client.getHospBasisList("110001", 2, 2).items().getFirst();

		assertThat(item.yadmNm()).isEqualTo("가상치과의원");
		assertThat(item.xPos()).isNull();
		assertThat(item.yPos()).isNull();
	}

	@Test
	void 서비스키는_URI_변수로_엄격하게_인코딩해_보낸다() throws IOException {
		respondWith("hosp-basis-list-page1.xml");

		client.getHospBasisList("110001", 1, 2);

		wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
				.withQueryParam("ServiceKey", equalTo(SERVICE_KEY))
				.withQueryParam("sgguCd", equalTo("110001"))
				.withQueryParam("pageNo", equalTo("1"))
				.withQueryParam("numOfRows", equalTo("2")));
		String rawUrl = wireMock.getAllServeEvents().getFirst().getRequest().getUrl();
		assertThat(rawUrl).contains("ServiceKey=" + ENCODED_SERVICE_KEY);
	}

	@Test
	void OpenAPI_ServiceResponse는_게이트웨이_오류로_분류한다() throws IOException {
		respondWith("gateway-error-22.xml");

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraGatewayException.class, e -> {
					assertThat(e.getReasonCode()).isEqualTo("22");
					assertThat(e.isDailyLimitExceeded()).isTrue();
					assertThat(e.isPerSecondLimitExceeded()).isFalse();
					assertThat(e.getAuthMessage()).isEqualTo("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR");
				});
	}

	@Test
	void resultCode가_00이_아니면_API_오류로_분류한다() throws IOException {
		respondWith("api-error.xml");

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraApiException.class, e -> {
					assertThat(e.getResultCode()).isEqualTo("10");
					assertThat(e.getResultMessage()).isEqualTo("INVALID_REQUEST_PARAMETER_ERROR");
				});
	}

	@Test
	void 본문으로_분류할_수_없는_HTTP_오류는_상태코드를_담는다() {
		wireMock.stubFor(get(anyUrl()).willReturn(serviceUnavailable()));

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isExactlyInstanceOf(HiraException.class)
				.hasMessageContaining("503");
	}

	@Test
	void 연결_실패_예외에_서비스키가_남지_않는다() {
		wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		Throwable thrown = catchThrowable(() -> client.getHospBasisList("110001", 1, 2));

		assertThat(thrown).isExactlyInstanceOf(HiraException.class);
		assertThat(stackTraceOf(thrown))
				.contains("getHospBasisList")
				.doesNotContain(SERVICE_KEY)
				.doesNotContain(ENCODED_SERVICE_KEY);
	}

	private static void respondWith(String fixture) throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/" + fixture).getContentAsByteArray();
		wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse()
				.withHeader("Content-Type", "application/xml;charset=UTF-8")
				.withBody(body)));
	}

	private static String stackTraceOf(Throwable throwable) {
		StringWriter out = new StringWriter();
		throwable.printStackTrace(new PrintWriter(out));
		return out.toString();
	}

}
