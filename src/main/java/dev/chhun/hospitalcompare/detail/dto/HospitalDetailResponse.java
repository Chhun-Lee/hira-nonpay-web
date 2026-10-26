package dev.chhun.hospitalcompare.detail.dto;

import dev.chhun.hospitalcompare.nonpay.dto.HospitalNonpayItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 병원 상세(F4).
 *
 * @param hospitalLevel 병원급인지(hospital.level-cl-codes). 아니면 상세·비급여는 공개 범위 밖이다
 * @param openStatus    지금 상태. 병원급이 아니면 null
 * @param judgedAt      판정 시각(한국 시간)
 * @param detail        세부정보. 없으면 null
 * @param subjects      진료과목(코드 순)
 * @param nonpayItems   이 병원이 공개한 비급여(중분류 → 이름 순)
 */
public record HospitalDetailResponse(
		String ykiho,
		String name,
		String clCd,
		String clCdNm,
		String address,
		String phone,
		Double latitude,
		Double longitude,
		Integer doctorCount,
		boolean hospitalLevel,
		OpenStatus openStatus,
		LocalDateTime judgedAt,
		DetailView detail,
		List<SubjectView> subjects,
		List<HospitalNonpayItem> nonpayItems,
		BaseDates baseDates) {

	/** 출처별 ACTIVE 기준일. 그 출처의 ACTIVE가 없으면 null */
	public record BaseDates(LocalDate hospitalList, LocalDate detail, LocalDate nonpay) {
	}

}
