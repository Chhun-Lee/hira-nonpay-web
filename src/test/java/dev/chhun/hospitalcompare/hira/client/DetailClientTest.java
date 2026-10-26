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
import dev.chhun.hospitalcompare.hira.dto.DetailPage;
import dev.chhun.hospitalcompare.hira.dto.DtlInfoItem;
import dev.chhun.hospitalcompare.hira.dto.SubjectItem;
import dev.chhun.hospitalcompare.hira.exception.HiraGatewayException;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

/**
 * 실제 API는 호출하지 않는다. 응답은 src/test/resources/fixtures/hira/detail/ 픽스처로 WireMock이 돌려준다.
 */
class DetailClientTest {

	private static final String SERVICE_KEY = "fake+Key/For==Test";
	private static final String BASE = "/MadmDtlInfoService2.8/";
	private static final HiraProperties.Retry FAST_RETRY =
			new HiraProperties.Retry(3, Duration.ofMillis(5), Duration.ofMillis(20));

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	private DetailClient client;

	@BeforeEach
	void setUp() {
		client = new DetailClient(RestClient.builder(), new HiraProperties(wireMock.baseUrl(), SERVICE_KEY,
				Duration.ofSeconds(3), FAST_RETRY, HiraProperties.HospInfo.DEFAULT, HiraProperties.Nonpay.DEFAULT,
				new HiraProperties.Detail(Duration.ofSeconds(1), 4, 1000)));
	}

	@Test
	void 세부정보를_읽고_ykiho와_서비스키를_보낸다() throws IOException {
		stub("getDtlInfo2.8", Map.of("ykiho", "Y-KIM"), "detail/dtl-info.xml", 200);

		DetailPage<DtlInfoItem> page = client.getDetail("Y-KIM", 1, 100);

		assertThat(page.totalCount()).isEqualTo(1);
		DtlInfoItem item = page.items().getFirst();
		assertThat(item.start(DayOfWeek.MONDAY)).isEqualTo("0830");
		assertThat(item.end(DayOfWeek.MONDAY)).isEqualTo("1700");
		assertThat(item.start(DayOfWeek.SUNDAY)).isEqualTo("0830");
		assertThat(item.end(DayOfWeek.SATURDAY)).isEqualTo("1230");
		assertThat(item.lunchWeek()).isEqualTo("12시30분~1시30분");
		assertThat(item.lunchSat()).isNull();
		assertThat(item.rcvWeek()).isEqualTo("08시00분~17시00분");
		assertThat(item.noTrmtHoli()).isNull();
		assertThat(item.emyDayYn()).isEqualTo("N");
		assertThat(item.parkQty()).isEqualTo("100");
		assertThat(item.parkXpnsYn()).isEqualTo("N");
		assertThat(item.parkEtc()).startsWith("외래진료차량");
		wireMock.verify(getRequestedFor(urlPathEqualTo(BASE + "getDtlInfo2.8"))
				.withQueryParam("serviceKey", equalTo(SERVICE_KEY))
				.withQueryParam("ykiho", equalTo("Y-KIM"))
				.withQueryParam("pageNo", equalTo("1"))
				.withQueryParam("numOfRows", equalTo("100")));
	}

	@Test
	void 세부정보가_없으면_빈_페이지다() throws IOException {
		stub("getDtlInfo2.8", Map.of("ykiho", "Y-ASAN"), "detail/dtl-info-empty.xml", 200);

		DetailPage<DtlInfoItem> page = client.getDetail("Y-ASAN", 1, 100);

		assertThat(page.items()).isEmpty();
		assertThat(page.totalCount()).isZero();
	}

	@Test
	void 진료과목과_과목별_전문의_수를_읽는다() throws IOException {
		stub("getDgsbjtInfo2.8", Map.of("ykiho", "Y-KIM"), "detail/subjects.xml", 200);

		DetailPage<SubjectItem> page = client.getSubjects("Y-KIM", 1, 100);

		assertThat(page.totalCount()).isEqualTo(5);
		assertThat(page.items()).extracting(SubjectItem::dgsbjtCd).containsExactly("01", "09", "12", "16", "19");
		assertThat(page.items().get(2)).isEqualTo(new SubjectItem("12", "안과", "47"));
	}

	@Test
	void 일_한도_오류는_게이트웨이_예외이고_서비스키를_드러내지_않는다() throws IOException {
		stub("getDtlInfo2.8", Map.of(), "gateway-error-22.xml", 403);

		assertThatThrownBy(() -> client.getDetail("Y-KIM", 1, 100))
				.isInstanceOf(HiraGatewayException.class)
				.satisfies(e -> assertThat(((HiraGatewayException) e).isDailyLimitExceeded()).isTrue())
				.hasMessageNotContaining(SERVICE_KEY);
	}

	private static void stub(String operation, Map<String, String> query, String fixture, int status)
			throws IOException {
		byte[] body = new ClassPathResource("fixtures/hira/" + fixture).getContentAsByteArray();
		var mapping = get(urlPathEqualTo(BASE + operation));
		for (Map.Entry<String, String> entry : query.entrySet()) {
			mapping = mapping.withQueryParam(entry.getKey(), equalTo(entry.getValue()));
		}
		wireMock.stubFor(mapping.willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/xml;charset=UTF-8")
				.withBody(body)));
	}

}
