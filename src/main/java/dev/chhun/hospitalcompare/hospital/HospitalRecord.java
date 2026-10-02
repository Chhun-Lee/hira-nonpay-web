package dev.chhun.hospitalcompare.hospital;

import java.time.LocalDate;

/**
 * hospital 테이블에 upsert할 한 행의 값. snapshot_id는 저장할 때 따로 받는다.
 */
public record HospitalRecord(
		String ykiho,
		String name,
		String clCd,
		String clCdNm,
		String sidoCd,
		String sidoCdNm,
		String sgguCd,
		String sgguCdNm,
		String emdongNm,
		String address,
		String phone,
		LocalDate establishedDate,
		Double latitude,
		Double longitude,
		Integer doctorCount) {
}
