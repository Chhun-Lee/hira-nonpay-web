package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.HospitalSubjectRecord;
import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.dto.WeeklyHours;
import dev.chhun.hospitalcompare.detail.entity.LunchKind;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import dev.chhun.hospitalcompare.hira.dto.DtlInfoItem;
import dev.chhun.hospitalcompare.hira.dto.SubjectItem;
import dev.chhun.hospitalcompare.snapshot.dto.QualityIssue;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 세부정보·진료과목 응답을 적재할 레코드로 바꾼다. 진료 시각·점심·휴진 안내를 해석하고, 못 읽은 값은 품질 이슈로 남긴다.
 * 세부정보 행은 해석에 실패해도 빼지 않는다(원문은 남긴다). 게이트는 진료 시각을 읽지 못한 칸의 비율을 본다.
 */
public class DetailRecordValidator {

	static final int TEXT_MAX = 500;
	static final int SUBJECT_NAME_MAX = 100;
	private static final Pattern COUNT = Pattern.compile("\\d{1,9}");

	public DetailResult validateDetail(String ykiho, DtlInfoItem item) {
		List<QualityIssue> issues = new ArrayList<>();
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(DayOfWeek.class);
		Set<DayOfWeek> unknownDays = EnumSet.noneOf(DayOfWeek.class);
		int hourFields = 0;
		int invalidHourFields = 0;
		for (DayOfWeek day : DayOfWeek.values()) {
			String start = item.start(day);
			String end = item.end(day);
			int present = (NonpayValues.isBlank(start) ? 0 : 1) + (NonpayValues.isBlank(end) ? 0 : 1);
			hourFields += present;
			DetailHoursParser.DayResult result = DetailHoursParser.parse(start, end);
			if (result.outcome() == DetailHoursParser.Outcome.VALID) {
				days.put(day, result.range());
			} else if (result.outcome() == DetailHoursParser.Outcome.INVALID) {
				unknownDays.add(day);
				invalidHourFields += present;
				issues.add(new QualityIssue(ykiho, QualityIssueType.HOURS_INVALID, day + " " + start + "~" + end));
			} else if (result.outcome() == DetailHoursParser.Outcome.AMBIGUOUS) {
				// 게이트의 읽지 못한 칸에는 넣지 않는다(형식 변경이 아니라 기관 입력 문제).
				unknownDays.add(day);
				issues.add(new QualityIssue(ykiho, QualityIssueType.HOURS_AMBIGUOUS, day + " " + start + "~" + end));
			}
		}
		HospitalDetailRecord record = new HospitalDetailRecord(
				ykiho,
				new WeeklyHours(days, unknownDays),
				lunch(ykiho, "lunchWeek", item.lunchWeek(), issues),
				lunch(ykiho, "lunchSat", item.lunchSat(), issues),
				text(ykiho, "rcvWeek", item.rcvWeek(), TEXT_MAX, issues),
				text(ykiho, "rcvSat", item.rcvSat(), TEXT_MAX, issues),
				note(ykiho, "noTrmtSun", item.noTrmtSun(), issues),
				note(ykiho, "noTrmtHoli", item.noTrmtHoli(), issues),
				yesNo(item.emyDayYn()),
				yesNo(item.emyNgtYn()),
				count(ykiho, "parkQty", item.parkQty(), issues),
				yesNo(item.parkXpnsYn()),
				text(ykiho, "parkEtc", item.parkEtc(), TEXT_MAX, issues));
		return new DetailResult(record, List.copyOf(issues), hourFields, invalidHourFields);
	}

	public SubjectResult validateSubjects(String ykiho, List<SubjectItem> items) {
		List<HospitalSubjectRecord> records = new ArrayList<>();
		List<QualityIssue> issues = new ArrayList<>();
		for (SubjectItem item : items) {
			if (NonpayValues.isBlank(item.dgsbjtCd())) {
				issues.add(new QualityIssue(ykiho, QualityIssueType.MISSING_REQUIRED,
						"dgsbjtCd 없음, dgsbjtCdNm=" + item.dgsbjtCdNm()));
				continue;
			}
			records.add(new HospitalSubjectRecord(ykiho, item.dgsbjtCd().strip(),
					text(ykiho, "dgsbjtCdNm", item.dgsbjtCdNm(), SUBJECT_NAME_MAX, issues),
					count(ykiho, "dgsbjtPrSdrCnt", item.dgsbjtPrSdrCnt(), issues)));
		}
		return new SubjectResult(List.copyOf(records), List.copyOf(issues));
	}

	private static Lunch lunch(String ykiho, String field, String raw, List<QualityIssue> issues) {
		Lunch lunch = LunchParser.parse(text(ykiho, field, raw, TEXT_MAX, issues));
		if (lunch.kind() == LunchKind.UNKNOWN) {
			issues.add(new QualityIssue(ykiho, QualityIssueType.LUNCH_UNPARSED, field + "=" + raw));
		}
		return lunch;
	}

	private static Note note(String ykiho, String field, String raw, List<QualityIssue> issues) {
		Note note = ClosedNoteClassifier.classify(text(ykiho, field, raw, TEXT_MAX, issues));
		if (note.kind() == NoteKind.UNKNOWN) {
			issues.add(new QualityIssue(ykiho, QualityIssueType.CLOSED_NOTE_UNCLASSIFIED, field + "=" + raw));
		}
		return note;
	}

	/** 앞뒤 공백을 지운 원문. 비어 있으면 null, 길면 잘라 두고 품질 이슈 */
	private static String text(String ykiho, String field, String raw, int max, List<QualityIssue> issues) {
		if (NonpayValues.isBlank(raw)) {
			return null;
		}
		String value = raw.strip();
		if (value.length() > max) {
			issues.add(new QualityIssue(ykiho, QualityIssueType.VALUE_TRUNCATED, field + "=" + value));
			return value.substring(0, max);
		}
		return value;
	}

	/** 0 이상의 정수. 비어 있으면 null, 정수가 아니면 null + 품질 이슈 */
	private static Integer count(String ykiho, String field, String raw, List<QualityIssue> issues) {
		if (NonpayValues.isBlank(raw)) {
			return null;
		}
		String value = raw.strip();
		if (!COUNT.matcher(value).matches()) {
			issues.add(new QualityIssue(ykiho, QualityIssueType.INVALID_COUNT, field + "=" + raw));
			return null;
		}
		return Integer.valueOf(value);
	}

	/** Y → true, N → false, 그 밖은 null */
	private static Boolean yesNo(String raw) {
		if (raw == null) {
			return null;
		}
		return switch (raw.strip()) {
			case "Y" -> Boolean.TRUE;
			case "N" -> Boolean.FALSE;
			default -> null;
		};
	}

	/**
	 * @param hourFields        받은 진료 시각 칸 수(시작·종료 각각)
	 * @param invalidHourFields 그중 읽지 못한 요일의 칸 수(게이트가 비율로 본다)
	 */
	public record DetailResult(HospitalDetailRecord record, List<QualityIssue> issues, int hourFields,
			int invalidHourFields) {
	}

	public record SubjectResult(List<HospitalSubjectRecord> records, List<QualityIssue> issues) {
	}

}
