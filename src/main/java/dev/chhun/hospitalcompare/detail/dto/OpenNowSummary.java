package dev.chhun.hospitalcompare.detail.dto;

import java.time.LocalDateTime;

/**
 * 지금 진료 중 필터의 결과 요약.
 *
 * @param judgedAt     판정 시각(한국 시간)
 * @param openCount    진료 중인 병원 수(상한으로 자르기 전)
 * @param unknownCount 판정 대상 중 확인 필요(진료시간 정보 없음 등)로 빠진 병원 수
 */
public record OpenNowSummary(LocalDateTime judgedAt, int openCount, int unknownCount) {
}
