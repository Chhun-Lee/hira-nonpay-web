package dev.chhun.hospitalcompare.nonpay.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 심평원 기준값(받은 그대로).
 *
 * @param stdDate    통계 기준일
 * @param nationwide 전국(종별 All). 없으면 null
 * @param sido       우리 시도. 반경 안에 병원이 없거나 코드·칸이 없으면 null
 * @param byType     종별(상급종합, 종합병원, 병원, 요양병원, 치과병원, 한방병원 순). 값이 있는 칸만
 */
public record ReferencePrices(LocalDate stdDate, StatValues nationwide, StatValues sido, List<StatValues> byType) {
}
