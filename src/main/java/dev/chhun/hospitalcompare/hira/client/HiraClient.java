package dev.chhun.hospitalcompare.hira.client;

import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.HospBasisListResponse;
import dev.chhun.hospitalcompare.hira.dto.HospBasisPage;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * 심평원 병원정보서비스 클라이언트. 호출과 오류 분류는 {@link HiraApiCaller}가 맡는다.
 */
public class HiraClient {

	private static final Logger log = LoggerFactory.getLogger(HiraClient.class);

	private static final String HOSP_BASIS_LIST = "/hospInfoServicev2/getHospBasisList"
			+ "?ServiceKey={serviceKey}&pageNo={pageNo}&numOfRows={numOfRows}";
	private static final String SGGU_CD_PARAM = "&sgguCd={sgguCd}";

	private final HiraApiCaller caller;

	public HiraClient(RestClient.Builder builder, HiraProperties properties) {
		this.caller = new HiraApiCaller(builder, properties, properties.hospInfo());
	}

	/**
	 * @param sgguCd 시군구코드. null이면 전국
	 */
	public HospBasisPage getHospBasisList(String sgguCd, int pageNo, int numOfRows) {
		String request = "getHospBasisList(sgguCd=" + (sgguCd == null ? "전국" : sgguCd) + ", pageNo=" + pageNo + ")";
		Map<String, Object> variables = new HashMap<>();
		variables.put("pageNo", pageNo);
		variables.put("numOfRows", numOfRows);
		String uriTemplate = HOSP_BASIS_LIST;
		if (sgguCd != null) {
			uriTemplate += SGGU_CD_PARAM;
			variables.put("sgguCd", sgguCd);
		}
		HospBasisPage page = caller.get(request, uriTemplate, variables, HospBasisListResponse.class).toPage();
		log.debug("{} {}건 / 전체 {}건", request, page.items().size(), page.totalCount());
		return page;
	}

	/** 이 클라이언트(병원정보서비스)로 보낸 호출의 누적 지표 */
	public HiraCallGate.Stats callStats() {
		return caller.stats();
	}

}
