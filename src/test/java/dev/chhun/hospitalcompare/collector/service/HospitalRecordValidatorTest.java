package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.hira.dto.HospBasisItem;
import dev.chhun.hospitalcompare.hospital.dto.HospitalRecord;
import dev.chhun.hospitalcompare.hospital.dto.QualityIssue;
import dev.chhun.hospitalcompare.hospital.entity.QualityIssueType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HospitalRecordValidatorTest {

	private final HospitalRecordValidator validator = new HospitalRecordValidator();

	@Test
	void 정상_행은_그대로_옮긴다() {
		HospitalRecordValidator.Result result = validator.validate(List.of(item("Y1", "병원", "31", 127.03, 37.50, "19950301")));

		assertThat(result.issues()).isEmpty();
		assertThat(result.missingRequired()).isZero();
		HospitalRecord record = result.records().getFirst();
		assertThat(record.ykiho()).isEqualTo("Y1");
		assertThat(record.latitude()).isEqualTo(37.50);
		assertThat(record.longitude()).isEqualTo(127.03);
		assertThat(record.establishedDate()).isEqualTo(LocalDate.of(1995, 3, 1));
	}

	@Test
	void 필수값이_빠진_행은_적재하지_않고_이슈를_남긴다() {
		HospitalRecordValidator.Result result = validator.validate(List.of(
				item(null, "병원", "31", 127.0, 37.5, null),
				item("Y2", " ", "31", 127.0, 37.5, null),
				item("Y3", "병원", null, 127.0, 37.5, null)));

		assertThat(result.records()).isEmpty();
		assertThat(result.missingRequired()).isEqualTo(3);
		assertThat(result.issues()).extracting(QualityIssue::type).containsOnly(QualityIssueType.MISSING_REQUIRED);
		assertThat(result.issues().get(1).ykiho()).isEqualTo("Y2");
	}

	@Test
	void 좌표가_없으면_비운_채_적재하고_이슈를_남긴다() {
		HospitalRecordValidator.Result result = validator.validate(List.of(item("Y1", "병원", "31", null, 37.5, null)));

		assertThat(result.records()).singleElement().satisfies(record -> {
			assertThat(record.latitude()).isNull();
			assertThat(record.longitude()).isNull();
		});
		assertThat(result.issues()).singleElement()
				.extracting(QualityIssue::type).isEqualTo(QualityIssueType.MISSING_COORDINATES);
	}

	@Test
	void 국내_범위_밖_좌표는_비우고_이슈를_남긴다() {
		HospitalRecordValidator.Result result = validator.validate(List.of(item("Y1", "병원", "31", 0.0, 0.0, null)));

		assertThat(result.records().getFirst().latitude()).isNull();
		assertThat(result.issues()).singleElement().satisfies(issue -> {
			assertThat(issue.type()).isEqualTo(QualityIssueType.COORDINATES_OUT_OF_RANGE);
			assertThat(issue.rawValue()).contains("XPos=0.0").contains("YPos=0.0");
		});
	}

	@Test
	void 날짜가_아닌_개설일은_비우고_이슈를_남긴다() {
		HospitalRecordValidator.Result result = validator.validate(List.of(item("Y1", "병원", "31", 127.0, 37.5, "00000000")));

		assertThat(result.records().getFirst().establishedDate()).isNull();
		assertThat(result.issues()).singleElement().satisfies(issue -> {
			assertThat(issue.type()).isEqualTo(QualityIssueType.INVALID_DATE);
			assertThat(issue.rawValue()).isEqualTo("estbDd=00000000");
		});
	}

	private static HospBasisItem item(String ykiho, String name, String clCd, Double xPos, Double yPos, String estbDd) {
		return new HospBasisItem(ykiho, name, clCd, "의원", "110000", "서울", "110001", "강남구", "역삼동",
				"서울특별시 강남구 가상로 1", "02-0000-0000", estbDd, xPos, yPos, 1);
	}

}
