package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.hira.dto.HospBasisItem;
import dev.chhun.hospitalcompare.hospital.dto.HospitalRecord;
import dev.chhun.hospitalcompare.hospital.dto.QualityIssue;
import dev.chhun.hospitalcompare.hospital.entity.QualityIssueType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 목록 응답의 기관 한 줄을 적재할 행으로 바꾸고, 문제가 있으면 품질 이슈로 남긴다.
 * 필수값(ykiho·기관명·종별코드)이 빠진 행만 버리고, 나머지 문제는 그 값만 비워 적재한다.
 */
public class HospitalRecordValidator {

	static final double MIN_LATITUDE = 33;
	static final double MAX_LATITUDE = 39;
	static final double MIN_LONGITUDE = 124;
	static final double MAX_LONGITUDE = 132;

	public Result validate(List<HospBasisItem> items) {
		List<HospitalRecord> records = new ArrayList<>();
		List<QualityIssue> issues = new ArrayList<>();
		int missingRequired = 0;

		for (HospBasisItem item : items) {
			if (isBlank(item.ykiho()) || isBlank(item.yadmNm()) || isBlank(item.clCd())) {
				issues.add(new QualityIssue(isBlank(item.ykiho()) ? null : item.ykiho(), QualityIssueType.MISSING_REQUIRED,
						"ykiho=" + item.ykiho() + ", yadmNm=" + item.yadmNm() + ", clCd=" + item.clCd()));
				missingRequired++;
				continue;
			}

			Double latitude = item.yPos();
			Double longitude = item.xPos();
			String coordinates = "XPos=" + longitude + ", YPos=" + latitude;
			if (latitude == null || longitude == null) {
				issues.add(new QualityIssue(item.ykiho(), QualityIssueType.MISSING_COORDINATES, coordinates));
				latitude = null;
				longitude = null;
			} else if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE
					|| longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
				issues.add(new QualityIssue(item.ykiho(), QualityIssueType.COORDINATES_OUT_OF_RANGE, coordinates));
				latitude = null;
				longitude = null;
			}

			LocalDate establishedDate = null;
			if (!isBlank(item.estbDd())) {
				try {
					establishedDate = LocalDate.parse(item.estbDd(), DateTimeFormatter.BASIC_ISO_DATE);
				} catch (DateTimeParseException e) {
					issues.add(new QualityIssue(item.ykiho(), QualityIssueType.INVALID_DATE, "estbDd=" + item.estbDd()));
				}
			}

			records.add(new HospitalRecord(item.ykiho(), item.yadmNm(), item.clCd(), item.clCdNm(),
					item.sidoCd(), item.sidoCdNm(), item.sgguCd(), item.sgguCdNm(), item.emdongNm(),
					item.addr(), item.telno(), establishedDate, latitude, longitude, item.drTotCnt()));
		}
		return new Result(records, issues, missingRequired);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * @param records         적재할 행
	 * @param issues          품질 이슈(버린 행 포함)
	 * @param missingRequired 필수값 누락으로 버린 행 수
	 */
	public record Result(List<HospitalRecord> records, List<QualityIssue> issues, int missingRequired) {
	}

}
