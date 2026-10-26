package dev.chhun.hospitalcompare.detail.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.dto.OpenState;
import dev.chhun.hospitalcompare.detail.dto.OpenStatus;
import dev.chhun.hospitalcompare.detail.dto.WeeklyHours;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 2026-10-07은 수요일, 10-10은 토요일, 10-11은 일요일이다.
 * 기본 병원: 월~금 08:30~17:00, 토 08:30~12:30, 일요일 칸 없음, 평일 점심 12:30~13:30.
 */
class OpenStatusJudgeTest {

	private static final Map<DayOfWeek, OpenRange> WEEK = week();
	private static final Lunch WEEK_LUNCH = Lunch.range(750, 810, "12:30~13:30");

	@Test
	void 세부정보가_없으면_진료시간_정보_없음이다() {
		assertThat(OpenStatusJudge.judge(null, wed(10, 0), false)).isEqualTo(OpenStatus.NO_INFO);
	}

	@Test
	void 진료_시각이_하나도_없으면_진료시간_정보_없음이다() {
		HospitalDetailRecord noHours = detail(WeeklyHours.NONE, WEEK_LUNCH, Note.EMPTY, Note.EMPTY);

		assertThat(OpenStatusJudge.judge(noHours, wed(10, 0), false)).isEqualTo(OpenStatus.NO_INFO);
	}

	@Test
	void 평일_진료_시간_안이면_진료_중이다() {
		assertThat(judge(wed(10, 0))).isEqualTo(OpenStatus.OPEN);
	}

	@Test
	void 시작_시각은_진료_중이고_종료_시각은_진료_끝이다() {
		assertThat(judge(wed(8, 29))).isEqualTo(OpenStatus.closed("진료 전"));
		assertThat(judge(wed(8, 30))).isEqualTo(OpenStatus.OPEN);
		assertThat(judge(wed(16, 59))).isEqualTo(OpenStatus.OPEN);
		assertThat(judge(wed(17, 0))).isEqualTo(OpenStatus.closed("진료 끝"));
	}

	@Test
	void 평일_점심시간은_진료_끝이다() {
		assertThat(judge(wed(12, 30))).isEqualTo(OpenStatus.closed("점심시간"));
		assertThat(judge(wed(13, 29))).isEqualTo(OpenStatus.closed("점심시간"));
		assertThat(judge(wed(13, 30))).isEqualTo(OpenStatus.OPEN);
	}

	@Test
	void 토요일은_토요일_점심만_본다() {
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(WEEK);
		days.put(DayOfWeek.SATURDAY, new OpenRange(540, 900));
		HospitalDetailRecord detail = new HospitalDetailRecord("Y", new WeeklyHours(days, Set.of()), WEEK_LUNCH,
				Lunch.none(null), null, null, Note.EMPTY, Note.EMPTY, null, null, null, null, null);

		// 평일 점심(12:30~13:30)이어도 토요일 점심이 없으면 진료 중이다.
		assertThat(OpenStatusJudge.judge(detail, sat(12, 40), false)).isEqualTo(OpenStatus.OPEN);
	}

	@Test
	void 요일_칸이_없으면_오늘_휴진이다() {
		assertThat(judge(sun(10, 0))).isEqualTo(OpenStatus.closed("오늘 휴진"));
	}

	@Test
	void 일요일_칸이_없는데_일요일_안내가_진료면_확인_필요다() {
		HospitalDetailRecord detail = detail(new WeeklyHours(WEEK, Set.of()), WEEK_LUNCH,
				new Note(NoteKind.OPEN, "24시간 진료"), Note.EMPTY);

		assertThat(OpenStatusJudge.judge(detail, sun(10, 0), false).status()).isEqualTo(OpenState.UNKNOWN);
	}

	/** 의원급 표본(스펙 §3.1): 일요일 0000~0000(모름) + 일요일 휴진 안내 "휴진" */
	@Test
	void 일요일_시각을_읽지_못했어도_일요일_휴진_안내면_휴진이다() {
		HospitalDetailRecord detail = detail(new WeeklyHours(WEEK, Set.of(DayOfWeek.SUNDAY)), WEEK_LUNCH,
				new Note(NoteKind.CLOSED, "휴진"), Note.EMPTY);

		assertThat(OpenStatusJudge.judge(detail, sun(10, 0), false)).isEqualTo(OpenStatus.closed("일요일 휴진"));
	}

	@Test
	void 일요일_시각이_있는데_일요일_휴진_안내면_확인_필요다() {
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(WEEK);
		days.put(DayOfWeek.SUNDAY, new OpenRange(540, 780));
		HospitalDetailRecord detail = detail(new WeeklyHours(days, Set.of()), WEEK_LUNCH,
				new Note(NoteKind.CLOSED, "전부 휴진"), Note.EMPTY);

		assertThat(OpenStatusJudge.judge(detail, sun(10, 0), false))
				.isEqualTo(OpenStatus.unknown("일요일 진료 여부 확인 필요"));
	}

	@Test
	void 일요일은_점심을_보지_않는다() {
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(WEEK);
		days.put(DayOfWeek.SUNDAY, new OpenRange(540, 780));
		HospitalDetailRecord detail = detail(new WeeklyHours(days, Set.of()), Lunch.range(720, 780, "12시~13시"),
				Note.EMPTY, Note.EMPTY);

		assertThat(OpenStatusJudge.judge(detail, sun(12, 10), false)).isEqualTo(OpenStatus.OPEN);
	}

	@Test
	void 공휴일_휴진_안내면_공휴일_휴진이다() {
		HospitalDetailRecord detail = withHolidayNote(new Note(NoteKind.CLOSED, "전부 휴진"));

		assertThat(OpenStatusJudge.judge(detail, wed(10, 0), true)).isEqualTo(OpenStatus.closed("공휴일 휴진"));
	}

	@Test
	void 공휴일_진료_안내면_그_요일_시간으로_보고_점심은_보지_않는다() {
		HospitalDetailRecord detail = withHolidayNote(new Note(NoteKind.OPEN, "정상진료"));

		assertThat(OpenStatusJudge.judge(detail, wed(12, 40), true)).isEqualTo(OpenStatus.OPEN);
		assertThat(OpenStatusJudge.judge(detail, wed(18, 0), true)).isEqualTo(OpenStatus.closed("진료 끝"));
	}

	@Test
	void 공휴일_안내가_없거나_모름이면_확인_필요다() {
		assertThat(OpenStatusJudge.judge(withHolidayNote(Note.EMPTY), wed(10, 0), true).status())
				.isEqualTo(OpenState.UNKNOWN);
		assertThat(OpenStatusJudge.judge(withHolidayNote(new Note(NoteKind.UNKNOWN, "개별공지")), wed(10, 0), true)
				.status()).isEqualTo(OpenState.UNKNOWN);
	}

	@Test
	void 시각을_읽지_못한_요일은_확인_필요다() {
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(WEEK);
		days.remove(DayOfWeek.WEDNESDAY);
		HospitalDetailRecord detail = detail(new WeeklyHours(days, Set.of(DayOfWeek.WEDNESDAY)), WEEK_LUNCH,
				Note.EMPTY, Note.EMPTY);

		assertThat(OpenStatusJudge.judge(detail, wed(10, 0), false))
				.isEqualTo(OpenStatus.unknown("오늘 진료시간 확인 필요"));
	}

	@Test
	void 점심을_읽지_못하면_12시부터_14시_전까지만_확인_필요다() {
		HospitalDetailRecord detail = detail(new WeeklyHours(WEEK, Set.of()), Lunch.unknown("점심+저녁"), Note.EMPTY,
				Note.EMPTY);

		assertThat(OpenStatusJudge.judge(detail, wed(11, 59), false)).isEqualTo(OpenStatus.OPEN);
		assertThat(OpenStatusJudge.judge(detail, wed(12, 0), false))
				.isEqualTo(OpenStatus.unknown("점심시간 확인 필요"));
		assertThat(OpenStatusJudge.judge(detail, wed(13, 59), false).status()).isEqualTo(OpenState.UNKNOWN);
		assertThat(OpenStatusJudge.judge(detail, wed(14, 0), false)).isEqualTo(OpenStatus.OPEN);
	}

	@Test
	void 스물네시간_진료는_하루_종일_진료_중이다() {
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(DayOfWeek.class);
		for (DayOfWeek day : DayOfWeek.values()) {
			days.put(day, new OpenRange(0, 1440));
		}
		HospitalDetailRecord detail = detail(new WeeklyHours(days, Set.of()), Lunch.none(null), Note.EMPTY,
				Note.EMPTY);

		assertThat(OpenStatusJudge.judge(detail, wed(0, 0), false)).isEqualTo(OpenStatus.OPEN);
		assertThat(OpenStatusJudge.judge(detail, wed(23, 59), false)).isEqualTo(OpenStatus.OPEN);
	}

	private static OpenStatus judge(LocalDateTime now) {
		return OpenStatusJudge.judge(detail(new WeeklyHours(WEEK, Set.of()), WEEK_LUNCH, Note.EMPTY, Note.EMPTY),
				now, false);
	}

	private static HospitalDetailRecord withHolidayNote(Note holidayNote) {
		return detail(new WeeklyHours(WEEK, Set.of()), Lunch.range(720, 780, "12시~13시"), Note.EMPTY, holidayNote);
	}

	private static HospitalDetailRecord detail(WeeklyHours hours, Lunch lunchWeek, Note sundayNote, Note holidayNote) {
		return new HospitalDetailRecord("Y-TEST", hours, lunchWeek, Lunch.none(null), null, null, sundayNote,
				holidayNote, false, false, null, null, null);
	}

	private static Map<DayOfWeek, OpenRange> week() {
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(DayOfWeek.class);
		for (DayOfWeek day : DayOfWeek.values()) {
			if (day.getValue() <= 5) {
				days.put(day, new OpenRange(510, 1020));
			}
		}
		days.put(DayOfWeek.SATURDAY, new OpenRange(510, 750));
		return days;
	}

	private static LocalDateTime wed(int hour, int minute) {
		return LocalDateTime.of(2026, 10, 7, hour, minute);
	}

	private static LocalDateTime sat(int hour, int minute) {
		return LocalDateTime.of(2026, 10, 10, hour, minute);
	}

	private static LocalDateTime sun(int hour, int minute) {
		return LocalDateTime.of(2026, 10, 11, hour, minute);
	}

}
