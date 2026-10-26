package dev.chhun.hospitalcompare.hira.client;

import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.DetailPage;
import dev.chhun.hospitalcompare.hira.dto.DtlInfoItem;
import dev.chhun.hospitalcompare.hira.dto.DtlInfoResponse;
import dev.chhun.hospitalcompare.hira.dto.SubjectItem;
import dev.chhun.hospitalcompare.hira.dto.SubjectListResponse;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * 심평원 의료기관별상세정보서비스 클라이언트. 기관마다(ykiho 필수) 부른다.
 * 병원정보·비급여와 따로 관문을 둔다(hira.detail.*).
 */
public class DetailClient {

	/** 서비스 버전. 바뀌면 여기만 고친다(2026-10-05 확인: 2.8, 포털 수정일 2026-05-29) */
	private static final String VERSION = "2.8";
	private static final String BASE = "/MadmDtlInfoService" + VERSION + "/";
	private static final String QUERY = "?serviceKey={serviceKey}&ykiho={ykiho}&pageNo={pageNo}&numOfRows={numOfRows}";

	private final HiraApiCaller caller;

	public DetailClient(RestClient.Builder builder, HiraProperties properties) {
		this.caller = new HiraApiCaller(builder, properties, properties.detail());
	}

	/** 세부정보(진료시간, 점심, 접수, 휴진 안내, 응급실, 주차). 세부정보가 없는 기관은 빈 페이지다. */
	public DetailPage<DtlInfoItem> getDetail(String ykiho, int pageNo, int numOfRows) {
		String operation = "getDtlInfo" + VERSION;
		return caller.get(operation + "(ykiho=" + ykiho + ", pageNo=" + pageNo + ")", BASE + operation + QUERY,
				params(ykiho, pageNo, numOfRows), DtlInfoResponse.class).toPage();
	}

	/** 진료과목과 과목별 전문의 수 */
	public DetailPage<SubjectItem> getSubjects(String ykiho, int pageNo, int numOfRows) {
		String operation = "getDgsbjtInfo" + VERSION;
		return caller.get(operation + "(ykiho=" + ykiho + ", pageNo=" + pageNo + ")", BASE + operation + QUERY,
				params(ykiho, pageNo, numOfRows), SubjectListResponse.class).toPage();
	}

	/** 이 클라이언트(상세정보서비스)로 보낸 호출의 누적 지표 */
	public HiraCallGate.Stats callStats() {
		return caller.stats();
	}

	private static Map<String, Object> params(String ykiho, int pageNo, int numOfRows) {
		return Map.of("ykiho", ykiho, "pageNo", pageNo, "numOfRows", numOfRows);
	}

}
