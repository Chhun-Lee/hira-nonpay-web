package dev.chhun.hospitalcompare.hira.client;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
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
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.HospBasisItem;
import dev.chhun.hospitalcompare.hira.dto.HospBasisPage;
import dev.chhun.hospitalcompare.hira.exception.HiraApiException;
import dev.chhun.hospitalcompare.hira.exception.HiraException;
import dev.chhun.hospitalcompare.hira.exception.HiraGatewayException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

	/** 재시도 대기 때문에 테스트가 느려지지 않게 짧게 둔다. */
	private static final HiraProperties.Retry FAST_RETRY =
			new HiraProperties.Retry(3, Duration.ofMillis(5), Duration.ofMillis(20));

	private HiraClient client;

	@BeforeEach
	void setUp() {
		HiraProperties properties = new HiraProperties(wireMock.baseUrl(), SERVICE_KEY,
				Duration.ofSeconds(3),
				FAST_RETRY, new HiraProperties.HospInfo(Duration.ofSeconds(1), 4, 1000), HiraProperties.Nonpay.DEFAULT);
		client = new HiraClient(RestClient.builder(), properties);
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
	void 일_한도_게이트웨이_오류는_재시도하지_않는다() throws IOException {
		respondWith("gateway-error-22.xml", 403);

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraGatewayException.class, e -> {
					assertThat(e.getReasonCode()).isEqualTo("22");
					assertThat(e.getErrorName()).isEqualTo("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR");
					assertThat(e.getAuthMessage()).isEqualTo("서비스 요청제한횟수 초과에러");
					assertThat(e.isDailyLimitExceeded()).isTrue();
					assertThat(e.isRetryable()).isFalse();
				});
	}

	@Test
	void 초당_한도_게이트웨이_오류는_재시도할_수_있다() throws IOException {
		respondWith("gateway-error-23.xml", 403);

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraGatewayException.class, e -> {
					assertThat(e.isPerSecondLimitExceeded()).isTrue();
					assertThat(e.isRetryable()).isTrue();
				});
	}

	@Test
	void HTTP_에러_게이트웨이_오류는_재시도할_수_있다() throws IOException {
		respondWith("gateway-error-04.xml", 403);

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraGatewayException.class, e -> {
					assertThat(e.getReasonCode()).isEqualTo("04");
					assertThat(e.getErrorName()).isEqualTo("HTTP_ERROR");
					assertThat(e.getAuthMessage()).isEqualTo("HTTP 에러");
					assertThat(e.isRetryable()).isTrue();
					assertThat(e.isPerSecondLimitExceeded()).isFalse();
				});
		// 첫 시도 1번과 재시도 3번
		wireMock.verify(4, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void 서비스_연결실패_게이트웨이_오류는_재시도할_수_있다() throws IOException {
		respondWith("gateway-error-05.xml", 403);

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraGatewayException.class, e -> {
					assertThat(e.getReasonCode()).isEqualTo("05");
					assertThat(e.getErrorName()).isEqualTo("SERVICETIMEOUT_ERROR");
					assertThat(e.getAuthMessage()).isEqualTo("서비스 연결실패 에러");
					assertThat(e.isRetryable()).isTrue();
					assertThat(e.isPerSecondLimitExceeded()).isFalse();
				});
		wireMock.verify(4, getRequestedFor(urlPathEqualTo(PATH)));
	}

	@Test
	void 미등록_키_게이트웨이_오류는_재시도하지_않는다() throws IOException {
		respondWith("gateway-error-30.xml", 403);

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraGatewayException.class, e -> {
					assertThat(e.getReasonCode()).isEqualTo("30");
					assertThat(e.getErrorName()).isEqualTo("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
					assertThat(e.isRetryable()).isFalse();
				});
	}

	@Test
	void API_오류코드는_재시도하지_않는다() throws IOException {
		respondWith("api-error.xml");

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraApiException.class, e -> assertThat(e.isRetryable()).isFalse());
	}

	@Test
	void 서버_오류_5xx는_재시도할_수_있다() {
		wireMock.stubFor(get(anyUrl()).willReturn(serviceUnavailable()));

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraException.class, e -> assertThat(e.isRetryable()).isTrue());
	}

	@Test
	void 요청_오류_4xx는_재시도하지_않는다() {
		wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(400)));

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraException.class, e -> assertThat(e.isRetryable()).isFalse());
	}

	@Test
	void 연결_실패는_재시도할_수_있다() {
		wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraException.class, e -> assertThat(e.isRetryable()).isTrue());
	}

	@Test
	void 응답이_읽기_제한을_넘으면_재시도할_수_있는_실패다() throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/hosp-basis-list-page1.xml").getContentAsByteArray();
		wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withFixedDelay(1500).withBody(body)));

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isInstanceOfSatisfying(HiraException.class, e -> assertThat(e.isRetryable()).isTrue());
	}

	@Test
	void 본문을_받다가_연결이_끊겨도_재시도할_수_있는_실패다() throws IOException {
		// 상태 줄과 헤더가 온 뒤 본문을 다 받기 전에 끊긴다. WireMock 오류 모사는 헤더 전에 끊어서 소켓으로 직접 응답한다.
		// 이때 RestClient는 ResourceAccessException이 아닌 RestClientException으로 감싼다.
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			Thread.ofVirtual().start(() -> truncateResponse(server));
			HiraProperties properties = new HiraProperties("http://127.0.0.1:" + server.getLocalPort(), SERVICE_KEY,
					Duration.ofSeconds(3),
					FAST_RETRY, new HiraProperties.HospInfo(Duration.ofSeconds(1), 4, 1000), HiraProperties.Nonpay.DEFAULT);
			HiraClient truncatedClient = new HiraClient(RestClient.builder(), properties);

			assertThatThrownBy(() -> truncatedClient.getHospBasisList("110001", 1, 2))
					.isInstanceOfSatisfying(HiraException.class, e -> assertThat(e.isRetryable()).isTrue());
		}
	}

	@Test
	void 일시적인_서버_오류는_재시도해서_받는다() throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/hosp-basis-list-page1.xml").getContentAsByteArray();
		wireMock.stubFor(get(urlPathEqualTo(PATH)).inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
				.willReturn(serviceUnavailable()).willSetStateTo("recovered"));
		wireMock.stubFor(get(urlPathEqualTo(PATH)).inScenario("retry").whenScenarioStateIs("recovered")
				.willReturn(aResponse().withHeader("Content-Type", "application/xml;charset=UTF-8").withBody(body)));

		HospBasisPage page = client.getHospBasisList("110001", 1, 2);

		assertThat(page.items()).hasSize(2);
		assertThat(client.callStats().retries()).isEqualTo(1);
	}

	@Test
	void 시군구를_주지_않으면_전국으로_요청한다() throws IOException {
		respondWith("hosp-basis-list-page1.xml");

		client.getHospBasisList(null, 1, 2);

		wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
				.withQueryParam("sgguCd", absent())
				.withQueryParam("pageNo", equalTo("1")));
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
	void 리다이렉트는_이동할_주소를_담되_서비스키는_가린다() {
		wireMock.stubFor(get(anyUrl()).willReturn(aResponse()
				.withStatus(302)
				.withHeader("Location", "http://example.com" + PATH + "?ServiceKey=" + ENCODED_SERVICE_KEY + "&pageNo=1")));

		assertThatThrownBy(() -> client.getHospBasisList("110001", 1, 2))
				.isExactlyInstanceOf(HiraException.class)
				.hasMessageContaining("302")
				.hasMessageContaining("http://example.com" + PATH + "?ServiceKey=****&pageNo=1")
				.message().doesNotContain(ENCODED_SERVICE_KEY);
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
		respondWith(fixture, 200);
	}

	private static void respondWith(String fixture, int status) throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/" + fixture).getContentAsByteArray();
		wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/xml;charset=UTF-8")
				.withBody(body)));
	}

	/** 요청마다 Content-Length보다 짧은 본문을 보낸 채 연결을 닫는다. 서버 소켓이 닫히면 끝난다(재시도해도 같은 응답). */
	private static void truncateResponse(ServerSocket server) {
		while (!server.isClosed()) {
			try (Socket socket = server.accept()) {
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
				String line;
				while ((line = in.readLine()) != null && !line.isEmpty()) {
					// 요청 헤더 끝까지 읽는다.
				}
				OutputStream out = socket.getOutputStream();
				String truncated = "HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\nContent-Length: 1000\r\n\r\n<response>";
				out.write(truncated.getBytes(StandardCharsets.UTF_8));
				out.flush();
			} catch (IOException e) {
				// 클라이언트가 먼저 끊거나 서버 소켓이 닫혀도 테스트에는 영향이 없다.
			}
		}
	}

	private static String stackTraceOf(Throwable throwable) {
		StringWriter out = new StringWriter();
		throwable.printStackTrace(new PrintWriter(out));
		return out.toString();
	}

}
