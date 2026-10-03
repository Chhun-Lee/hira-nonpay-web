package dev.chhun.hospitalcompare.hira.client;

import dev.chhun.hospitalcompare.hira.config.HiraProperties;
import dev.chhun.hospitalcompare.hira.dto.NonpayHospListResponse;
import dev.chhun.hospitalcompare.hira.dto.NonpayHospPrice;
import dev.chhun.hospitalcompare.hira.dto.NonpayItemCode;
import dev.chhun.hospitalcompare.hira.dto.NonpayItemCodeListResponse;
import dev.chhun.hospitalcompare.hira.dto.NonpayPage;
import dev.chhun.hospitalcompare.hira.dto.NonpayStatListResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * 심평원 비급여진료비정보서비스 클라이언트. 병원급 이상만 제공한다(의원급 없음).
 * 응답이 느려(10건도 18초) 병원정보서비스와 다른 읽기 제한·관문을 쓴다.
 */
public class NonpayClient {

	private static final Logger log = LoggerFactory.getLogger(NonpayClient.class);

	private static final String BASE = "/nonPaymentDamtInfoService/";
	private static final String PAGE = "?ServiceKey={serviceKey}&pageNo={pageNo}&numOfRows={numOfRows}";

	private final HiraApiCaller caller;

	public NonpayClient(RestClient.Builder builder, HiraProperties properties) {
		this.caller = new HiraApiCaller(builder, properties, properties.nonpay());
	}

	public NonpayPage<NonpayItemCode> getItemCodes(int pageNo, int numOfRows) {
		return caller.get("getNonPaymentItemCodeList2(pageNo=" + pageNo + ")",
				BASE + "getNonPaymentItemCodeList2" + PAGE, page(pageNo, numOfRows),
				NonpayItemCodeListResponse.class).toPage();
	}

	public NonpayPage<NonpayHospPrice> getHospPrices(String itemCd, int pageNo, int numOfRows) {
		String request = "getNonPaymentItemHospList2(itemCd=" + itemCd + ", pageNo=" + pageNo + ")";
		NonpayPage<NonpayHospPrice> page = caller.get(request,
				BASE + "getNonPaymentItemHospList2" + PAGE + "&itemCd={itemCd}",
				Map.of("pageNo", pageNo, "numOfRows", numOfRows, "itemCd", itemCd),
				NonpayHospListResponse.class).toPage();
		log.debug("{} {}건 / 전체 {}건", request, page.items().size(), page.totalCount());
		return page;
	}

	/** 항목별 종별(상급종합, 종합병원 …) 최소·최대·평균·중간값 */
	public NonpayPage<Map<String, String>> getTypeStats(int pageNo, int numOfRows) {
		return caller.get("getNonPaymentItemClcdList(pageNo=" + pageNo + ")",
				BASE + "getNonPaymentItemClcdList" + PAGE, page(pageNo, numOfRows),
				NonpayStatListResponse.class).toPage();
	}

	/** 항목별 시도별 최소·최대·평균·중간값 */
	public NonpayPage<Map<String, String>> getSidoStats(int pageNo, int numOfRows) {
		return caller.get("getNonPaymentItemSidoCdList(pageNo=" + pageNo + ")",
				BASE + "getNonPaymentItemSidoCdList" + PAGE, page(pageNo, numOfRows),
				NonpayStatListResponse.class).toPage();
	}

	/** 이 클라이언트(비급여진료비정보서비스)로 보낸 호출의 누적 지표 */
	public HiraCallGate.Stats callStats() {
		return caller.stats();
	}

	private static Map<String, Object> page(int pageNo, int numOfRows) {
		return Map.of("pageNo", pageNo, "numOfRows", numOfRows);
	}

}
