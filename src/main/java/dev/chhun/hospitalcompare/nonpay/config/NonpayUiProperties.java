package dev.chhun.hospitalcompare.nonpay.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 비급여 비교 화면 설정.
 *
 * @param featuredItems 빠른 선택 버튼(이 순서대로)
 * @param hiraNonpayUrl 의원급 비급여 가격을 안내할 심평원 누리집 화면 주소
 */
@ConfigurationProperties("noncovered")
public record NonpayUiProperties(
		@DefaultValue List<FeaturedItem> featuredItems,
		@DefaultValue("https://www.hira.or.kr") String hiraNonpayUrl) {

	/**
	 * @param code  비급여 항목 코드
	 * @param label 버튼 이름
	 */
	public record FeaturedItem(String code, String label) {
	}

}
