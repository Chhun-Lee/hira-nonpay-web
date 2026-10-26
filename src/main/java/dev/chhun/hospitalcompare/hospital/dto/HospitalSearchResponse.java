package dev.chhun.hospitalcompare.hospital.dto;

import dev.chhun.hospitalcompare.detail.dto.OpenNowSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * @param baseDate 결과가 나온 스냅샷의 기준일. active 스냅샷이 없으면 null
 * @param openNow  지금 진료 중 필터를 켰을 때만 있다
 */
public record HospitalSearchResponse(LocalDate baseDate, List<NearbyHospital> hospitals, OpenNowSummary openNow) {
}
