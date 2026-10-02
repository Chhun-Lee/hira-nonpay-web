package dev.chhun.hospitalcompare.hira.client;

import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.GatewayErrorResponse;
import dev.chhun.hospitalcompare.hira.dto.HospBasisListResponse;
import dev.chhun.hospitalcompare.hira.dto.HospBasisPage;
import dev.chhun.hospitalcompare.hira.exception.HiraApiException;
import dev.chhun.hospitalcompare.hira.exception.HiraException;
import dev.chhun.hospitalcompare.hira.exception.HiraGatewayException;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * 심평원 병원정보서비스 클라이언트.
 * <p>
 * 오류는 HTTP 200 본문으로도 오므로 상태 코드보다 응답 루트 요소로 먼저 분류한다.
 * {@code <OpenAPI_ServiceResponse>}는 게이트웨이 오류, {@code <response>}는 resultCode가 00이 아니면 API 오류다.
 * 서비스 키는 URI 변수로만 넘기고, 요청 URL이 담긴 예외는 밖으로 내보내지 않는다.
 */
public class HiraClient {

	private static final Logger log = LoggerFactory.getLogger(HiraClient.class);

	private static final String HOSP_BASIS_LIST = "/hospInfoServicev2/getHospBasisList"
			+ "?ServiceKey={serviceKey}&sgguCd={sgguCd}&pageNo={pageNo}&numOfRows={numOfRows}";

	private static final Pattern SERVICE_KEY_PARAM = Pattern.compile("(?i)(serviceKey=)[^&\\s\"]*");

	private static final XMLInputFactory XML_INPUT = createXmlInputFactory();

	private final RestClient restClient;
	private final String serviceKey;
	private final XmlMapper xmlMapper = XmlMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	public HiraClient(RestClient.Builder builder, HiraProperties properties) {
		// 값까지 엄격하게 인코딩해야 Decoding 키의 +, /, = 가 그대로 전달된다.
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(properties.baseUrl());
		uriBuilderFactory.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);
		this.restClient = builder.uriBuilderFactory(uriBuilderFactory).build();
		this.serviceKey = properties.serviceKey();
	}

	public HospBasisPage getHospBasisList(String sgguCd, int pageNo, int numOfRows) {
		String request = "getHospBasisList(sgguCd=" + sgguCd + ", pageNo=" + pageNo + ")";
		byte[] body = call(request, HOSP_BASIS_LIST,
				Map.of("serviceKey", serviceKey, "sgguCd", sgguCd, "pageNo", pageNo, "numOfRows", numOfRows));

		HospBasisListResponse response = parse(request, body, HospBasisListResponse.class);
		HospBasisListResponse.Header header = response.header();
		if (header == null || !"00".equals(header.resultCode())) {
			throw new HiraApiException(request,
					header == null ? null : header.resultCode(),
					header == null ? null : header.resultMsg());
		}
		if (response.body() == null) {
			throw new HiraException(request + " 응답에 body가 없습니다");
		}

		HospBasisPage page = response.toPage();
		log.debug("{} {}건 / 전체 {}건", request, page.items().size(), page.totalCount());
		return page;
	}

	/**
	 * 호출해서 게이트웨이 오류와 HTTP 오류를 걸러 내고 {@code <response>} 본문만 돌려준다.
	 */
	private byte[] call(String request, String uriTemplate, Map<String, ?> uriVariables) {
		ResponseEntity<byte[]> response;
		try {
			response = restClient.get()
					.uri(uriTemplate, uriVariables)
					.retrieve()
					// 게이트웨이 오류가 4xx·5xx로 와도 본문으로 분류하도록 여기서 던지지 않는다.
					.onStatus(HttpStatusCode::isError, (req, res) -> {
					})
					.toEntity(byte[].class);
		} catch (RestClientException e) {
			// RestClient 예외 메시지에는 서비스 키가 든 요청 URL이 있어 원인 예외로 붙이지 않는다.
			throw new HiraException(request + " 호출 실패: " + maskServiceKey(e.getMostSpecificCause().toString()));
		}

		byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
		String root = rootElementName(body);
		if ("OpenAPI_ServiceResponse".equals(root)) {
			GatewayErrorResponse.CmmMsgHeader header = parse(request, body, GatewayErrorResponse.class).cmmMsgHeader();
			throw new HiraGatewayException(request,
					header == null ? null : header.returnReasonCode(),
					header == null ? null : header.returnAuthMsg());
		}
		if (!response.getStatusCode().is2xxSuccessful()) {
			// 리다이렉트는 따라가지 않는다. 원인을 볼 수 있게 이동할 주소를 남기되 서비스 키는 가린다.
			String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
			throw new HiraException(request + " HTTP " + response.getStatusCode().value()
					+ (location == null ? "" : " Location " + maskServiceKey(location)));
		}
		if (!"response".equals(root)) {
			throw new HiraException(request + " 알 수 없는 응답 형식(루트 요소 " + root + ")");
		}
		return body;
	}

	private <T> T parse(String request, byte[] body, Class<T> type) {
		try {
			return xmlMapper.readValue(body, type);
		} catch (JacksonException e) {
			throw new HiraException(request + " 응답 해석 실패: " + e.getOriginalMessage());
		}
	}

	private static String maskServiceKey(String text) {
		return SERVICE_KEY_PARAM.matcher(text).replaceAll("$1****");
	}

	/** XML이 아닌 본문(빈 본문, HTML 오류 페이지 등)이면 null */
	private static String rootElementName(byte[] body) {
		try {
			XMLStreamReader reader = XML_INPUT.createXMLStreamReader(new ByteArrayInputStream(body));
			try {
				while (reader.hasNext()) {
					if (reader.next() == XMLStreamConstants.START_ELEMENT) {
						return reader.getLocalName();
					}
				}
				return null;
			} finally {
				reader.close();
			}
		} catch (XMLStreamException e) {
			return null;
		}
	}

	private static XMLInputFactory createXmlInputFactory() {
		XMLInputFactory factory = XMLInputFactory.newFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		return factory;
	}

}
