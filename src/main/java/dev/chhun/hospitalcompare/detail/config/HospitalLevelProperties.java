package dev.chhun.hospitalcompare.detail.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 병원급 종별. 상세정보 수집 대상이자 화면의 "병원급"(진료시간·비급여 공개 범위) 판단 기준이다.
 *
 * @param levelClCodes 01 상급종합, 11 종합병원, 21 병원, 28 요양병원, 29 정신병원, 41 치과병원, 92 한방병원
 */
@ConfigurationProperties("hospital")
public record HospitalLevelProperties(
		@DefaultValue({"01", "11", "21", "28", "29", "41", "92"}) List<String> levelClCodes) {

	public HospitalLevelProperties {
		if (levelClCodes.isEmpty()) {
			throw new IllegalStateException("hospital.level-cl-codes가 비어 있습니다");
		}
		levelClCodes = List.copyOf(levelClCodes);
	}

	public boolean isHospitalLevel(String clCd) {
		return clCd != null && levelClCodes.contains(clCd);
	}

}
