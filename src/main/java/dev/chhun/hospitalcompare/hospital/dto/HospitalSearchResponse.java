package dev.chhun.hospitalcompare.hospital.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param baseDate 결과가 나온 스냅샷의 기준일. active 스냅샷이 없으면 null
 */
public record HospitalSearchResponse(LocalDate baseDate, List<NearbyHospital> hospitals) {
}
