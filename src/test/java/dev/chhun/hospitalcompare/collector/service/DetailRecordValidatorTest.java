package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.HospitalSubjectRecord;
import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.entity.LunchKind;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import dev.chhun.hospitalcompare.hira.dto.DtlInfoItem;
import dev.chhun.hospitalcompare.hira.dto.SubjectItem;
import dev.chhun.hospitalcompare.snapshot.dto.QualityIssue;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DetailRecordValidatorTest {

	private final DetailRecordValidator validator = new DetailRecordValidator();

	@Test
	void 김안과병원_세부정보를_해석한다() {
		DtlInfoItem item = new DtlInfoItem("0830", "1700", "0830", "1700", "0830", "1700", "0830", "1700", "0830",
				"1700", "0830", "1230", "0830", "1230", "12시30분~1시30분", null, "08시00분~17시00분", "08시00분~12시00분",
				null, null, "N", "N", "100", "N", "외래진료차량-주차권 확인시 무료/퇴원 차량-당일 4시간 무료");

		DetailRecordValidator.DetailResult result = validator.validateDetail("Y-KIM", item);

		HospitalDetailRecord record = result.record();
		assertThat(record.hours().days()).hasSize(7)
				.containsEntry(DayOfWeek.MONDAY, new OpenRange(510, 1020))
				.containsEntry(DayOfWeek.SUNDAY, new OpenRange(510, 750));
		assertThat(record.hours().unknownDays()).isEmpty();
		assertThat(record.lunchWeek()).isEqualTo(Lunch.range(750, 810, "12시30분~1시30분"));
		assertThat(record.lunchSat()).isEqualTo(Lunch.none(null));
		assertThat(record.receptionWeek()).isEqualTo("08시00분~17시00분");
		assertThat(record.sundayNote()).isEqualTo(Note.EMPTY);
		assertThat(record.emergencyDay()).isFalse();
		assertThat(record.parkingCapacity()).isEqualTo(100);
		assertThat(record.parkingFee()).isFalse();
		assertThat(record.parkingNote()).startsWith("외래진료차량");
		assertThat(result.issues()).isEmpty();
		assertThat(result.hourFields()).isEqualTo(14);
		assertThat(result.invalidHourFields()).isZero();
	}

	@Test
	void 시각을_읽지_못한_요일은_모름이고_칸_수를_센다() {
		DtlInfoItem item = new DtlInfoItem("0900", "1800", "0900", "1800", "0900", null, "08:30", "17:00", "0900",
				"1800", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

		DetailRecordValidator.DetailResult result = validator.validateDetail("Y-ODD", item);

		assertThat(result.record().hours().days()).containsOnlyKeys(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
				DayOfWeek.FRIDAY);
		assertThat(result.record().hours().unknownDays()).isEqualTo(Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY));
		// 월 2 + 화 2 + 수 1(시작만) + 목 2 + 금 2. 읽지 못한 칸은 수 1 + 목 2
		assertThat(result.hourFields()).isEqualTo(9);
		assertThat(result.invalidHourFields()).isEqualTo(3);
		assertThat(result.issues()).extracting(QualityIssue::type)
				.containsExactly(QualityIssueType.HOURS_INVALID, QualityIssueType.HOURS_INVALID);
		assertThat(result.issues().getFirst().rawValue()).isEqualTo("WEDNESDAY 0900~null");
	}

	/** 연세가정의원(2026-10-26 의원급 표본 실제 응답). 오후를 12시간제로, 휴진 일요일을 0000~0000으로 적었다. */
	@Test
	void 열두시간제와_자리채움_시각은_모름이고_게이트_칸_수에는_들지_않는다() {
		DtlInfoItem item = new DtlInfoItem("0900", "0630", "0900", "0630", "0900", "0630", "0900", "0630", "0900",
				"0630", "0900", "0300", "0000", "0000", "13시00분-14시00분", "13시00분-14시00분", null, null, "휴진",
				"오전9시- 오후3시까지 진료", null, null, null, null, null);

		DetailRecordValidator.DetailResult result = validator.validateDetail("Y-12H", item);

		assertThat(result.record().hours().days()).isEmpty();
		assertThat(result.record().hours().unknownDays()).containsExactlyInAnyOrder(DayOfWeek.values());
		assertThat(result.record().lunchWeek()).isEqualTo(Lunch.range(780, 840, "13시00분-14시00분"));
		assertThat(result.record().sundayNote()).isEqualTo(new Note(NoteKind.CLOSED, "휴진"));
		assertThat(result.hourFields()).isEqualTo(14);
		assertThat(result.invalidHourFields()).isZero();
		assertThat(result.issues()).extracting(QualityIssue::type).containsExactly(
				QualityIssueType.HOURS_AMBIGUOUS, QualityIssueType.HOURS_AMBIGUOUS, QualityIssueType.HOURS_AMBIGUOUS,
				QualityIssueType.HOURS_AMBIGUOUS, QualityIssueType.HOURS_AMBIGUOUS, QualityIssueType.HOURS_AMBIGUOUS,
				QualityIssueType.HOURS_AMBIGUOUS, QualityIssueType.CLOSED_NOTE_UNCLASSIFIED);
		assertThat(result.issues().getFirst().rawValue()).isEqualTo("MONDAY 0900~0630");
		assertThat(result.issues().get(6).rawValue()).isEqualTo("SUNDAY 0000~0000");
	}

	@Test
	void 읽지_못한_점심과_휴진_안내는_원문을_두고_품질_이슈로_남긴다() {
		DtlInfoItem item = new DtlInfoItem("0900", "1800", null, null, null, null, null, null, null, null, null, null,
				null, null, "점심 : 13시00분 ~ 14시00분, 저녁 : 18시00분 ~ 19시00분", null, null, null, "개별공지", "전부 휴진",
				"Y", null, null, null, null);

		DetailRecordValidator.DetailResult result = validator.validateDetail("Y-ODD", item);

		assertThat(result.record().lunchWeek().kind()).isEqualTo(LunchKind.UNKNOWN);
		assertThat(result.record().lunchWeek().text()).startsWith("점심 : 13시00분");
		assertThat(result.record().sundayNote()).isEqualTo(new Note(NoteKind.UNKNOWN, "개별공지"));
		assertThat(result.record().holidayNote()).isEqualTo(new Note(NoteKind.CLOSED, "전부 휴진"));
		assertThat(result.record().emergencyDay()).isTrue();
		assertThat(result.record().emergencyNight()).isNull();
		assertThat(result.issues()).extracting(QualityIssue::type)
				.containsExactly(QualityIssueType.LUNCH_UNPARSED, QualityIssueType.CLOSED_NOTE_UNCLASSIFIED);
	}

	@Test
	void 정수가_아닌_주차_대수는_비우고_긴_원문은_잘라_둔다() {
		String longNote = "가".repeat(600);
		DtlInfoItem item = new DtlInfoItem("0900", "1800", null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, "약 50", "Y", longNote);

		DetailRecordValidator.DetailResult result = validator.validateDetail("Y-ODD", item);

		assertThat(result.record().parkingCapacity()).isNull();
		assertThat(result.record().parkingFee()).isTrue();
		assertThat(result.record().parkingNote()).hasSize(500);
		assertThat(result.issues()).extracting(QualityIssue::type)
				.containsExactly(QualityIssueType.INVALID_COUNT, QualityIssueType.VALUE_TRUNCATED);
	}

	@Test
	void 진료과목은_코드_없는_행을_빼고_정수가_아닌_전문의_수는_비운다() {
		DetailRecordValidator.SubjectResult result = validator.validateSubjects("Y-KIM", List.of(
				new SubjectItem("12", "안과", "47"),
				new SubjectItem(" ", "이름만", "1"),
				new SubjectItem("01", "내과", "-")));

		assertThat(result.records()).containsExactly(
				new HospitalSubjectRecord("Y-KIM", "12", "안과", 47),
				new HospitalSubjectRecord("Y-KIM", "01", "내과", null));
		assertThat(result.issues()).extracting(QualityIssue::type)
				.containsExactly(QualityIssueType.MISSING_REQUIRED, QualityIssueType.INVALID_COUNT);
	}

}
