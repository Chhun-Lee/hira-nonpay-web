package dev.chhun.hospitalcompare.hira.client;

import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.GatewayErrorResponse;
import dev.chhun.hospitalcompare.hira.dto.HiraHeader;
import dev.chhun.hospitalcompare.hira.dto.HiraResponse;
import dev.chhun.hospitalcompare.hira.exception.HiraApiException;
import dev.chhun.hospitalcompare.hira.exception.HiraException;
import dev.chhun.hospitalcompare.hira.exception.HiraGatewayException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * 심평원 서비스 하나를 부르는 공통 호출부. 서비스마다 하나씩 만든다(읽기 제한과 관문이 서비스 단위).
 * <p>
 * 오류는 HTTP 200 본문으로도 오므로 상태 코드보다 응답 루트 요소로 먼저 분류한다.
 * {@code <OpenAPI_ServiceResponse>}는 게이트웨이 오류, {@code <response>}는 resultCode가 00이 아니면 API 오류다.
 * 서비스 키는 URI 변수로만 넘기고, 요청 URL이 담긴 예외는 밖으로 내보내지 않는다.
 */
public class HiraApiCaller {

	private static final Pattern SERVICE_KEY_PARAM = Pattern.compile("(?i)(serviceKey=)[^&\\s\"]*");

	private static final XMLInputFactory XML_INPUT = createXmlInputFactory();

	private final RestClient restClient;
	private final String serviceKey;
	private final HiraCallGate callGate;
	private final XmlMapper xmlMapper = XmlMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	public HiraApiCaller(RestClient.Builder builder, HiraProperties properties,
			HiraProperties.ServiceSettings service) {
		// 값까지 엄격하게 인코딩해야 Decoding 키의 +, /, = 가 그대로 전달된다.
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(properties.baseUrl());
		uriBuilderFactory.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);
		// 응답이 오지 않으면 무한정 기다리지 않는다. 리다이렉트는 따라가지 않는다(HttpClient 기본값).
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(service.readTimeout());
		this.restClient = builder.uriBuilderFactory(uriBuilderFactory).requestFactory(requestFactory).build();
		this.serviceKey = properties.serviceKey();
		this.callGate = HiraCallGate.create(service, properties.retry());
	}

	/**
	 * 관문을 거쳐 GET 하나를 보내고 {@code <response>} 본문을 type으로 읽는다. 재시도도 관문을 다시 지난다.
	 *
	 * @param request     로그·예외 메시지에 쓸 요청 이름. 서비스 키를 넣지 않는다.
	 * @param uriTemplate {@code {serviceKey}} 변수를 포함한 경로와 쿼리
	 */
	public <T extends HiraResponse> T get(String request, String uriTemplate, Map<String, ?> uriVariables,
			Class<T> type) {
		return callGate.call(() -> {
			Map<String, Object> variables = new HashMap<>();
			variables.putAll(uriVariables);
			variables.put("serviceKey", serviceKey);
			T response = parse(request, call(request, uriTemplate, variables), type);
			HiraHeader header = response.header();
			if (header == null || !"00".equals(header.resultCode())) {
				throw new HiraApiException(request,
						header == null ? null : header.resultCode(),
						header == null ? null : header.resultMsg());
			}
			if (!response.hasBody()) {
				throw new HiraException(request + " 응답에 body가 없습니다");
			}
			return response;
		});
	}

	/** 이 서비스로 보낸 호출의 누적 지표 */
	public HiraCallGate.Stats stats() {
		return callGate.stats();
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
			// 연결 실패·타임아웃 같은 I/O 실패는 잠시 뒤 다시 보내면 풀릴 수 있다.
			throw new HiraException(request + " 호출 실패: " + maskServiceKey(e.getMostSpecificCause().toString()),
					isIoFailure(e));
		}

		byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
		String root = rootElementName(body);
		if ("OpenAPI_ServiceResponse".equals(root)) {
			GatewayErrorResponse.CmmMsgHeader header = parse(request, body, GatewayErrorResponse.class).cmmMsgHeader();
			throw new HiraGatewayException(request,
					header == null ? null : header.returnReasonCode(),
					header == null ? null : header.errMsg(),
					header == null ? null : header.returnAuthMsg());
		}
		if (!response.getStatusCode().is2xxSuccessful()) {
			// 리다이렉트는 따라가지 않는다. 원인을 볼 수 있게 이동할 주소를 남기되 서비스 키는 가린다.
			String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
			throw new HiraException(request + " HTTP " + response.getStatusCode().value()
					+ (location == null ? "" : " Location " + maskServiceKey(location)),
					response.getStatusCode().is5xxServerError());
		}
		if (!"response".equals(root)) {
			throw new HiraException(request + " 알 수 없는 응답 형식(루트 요소 " + root + ")");
		}
		return body;
	}

	/**
	 * 요청을 보내다 실패하면 ResourceAccessException으로 오지만, 상태 줄과 헤더가 온 뒤 본문을 읽다가 끊기거나
	 * 읽기 제한을 넘으면 RestClient가 일반 RestClientException으로 감싼다. 원인에 IOException이 있으면 같은 I/O 실패로 본다.
	 */
	private static boolean isIoFailure(RestClientException e) {
		if (e instanceof ResourceAccessException) {
			return true;
		}
		for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof IOException) {
				return true;
			}
		}
		return false;
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
