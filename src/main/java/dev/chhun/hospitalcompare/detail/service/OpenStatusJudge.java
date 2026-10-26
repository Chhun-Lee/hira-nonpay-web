package dev.chhun.hospitalcompare.detail.service;

import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.dto.OpenStatus;
import dev.chhun.hospitalcompare.detail.entity.LunchKind;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * 지금 진료 중인지 정한다(스펙 §8). F4 배지와 F5 필터가 모두 이 함수만 쓴다.
 * 순서: 정보 없음 → 공휴일 안내 → 오늘 요일 시간(일요일은 일요일 휴진 안내와 함께) → 진료 시간 안인지 → 점심(평일·토요일만).
 * 진료 시간은 하루 안에서 끝나므로 오늘 요일만 본다(자정을 넘는 진료는 해석기가 "모름"으로 둔다).
 */
public final class OpenStatusJudge {

	/** 점심을 읽지 못했을 때 확인 필요로 둘 시간대(12:00 ~ 14:00 전) */
	static final int LUNCH_GUESS_START = 12 * 60;
	static final int LUNCH_GUESS_END = 14 * 60;

	private OpenStatusJudge() {
	}

	/**
	 * @param detail  세부정보. 없으면 null
	 * @param now     지금(한국 시간)
	 * @param holiday 오늘이 공휴일인지
	 */
	public static OpenStatus judge(HospitalDetailRecord detail, LocalDateTime now, boolean holiday) {
		if (detail == null || !detail.hours().hasAny()) {
			return OpenStatus.NO_INFO;
		}
		if (holiday) {
			NoteKind kind = detail.holidayNote().kind();
			if (kind == NoteKind.CLOSED) {
				return OpenStatus.closed("공휴일 휴진");
			}
			if (kind != NoteKind.OPEN) {
				return OpenStatus.unknown("공휴일 진료 여부 확인 필요");
			}
		}
		DayOfWeek day = now.getDayOfWeek();
		NoteKind sundayNote = day == DayOfWeek.SUNDAY ? detail.sundayNote().kind() : null;
		if (detail.hours().unknownDays().contains(day)) {
			// 의원급 표본: 일요일 0000~0000(모름)인데 일요일 휴진 안내가 "휴진"이었다. 안내를 따른다.
			if (sundayNote == NoteKind.CLOSED) {
				return OpenStatus.closed("일요일 휴진");
			}
			return OpenStatus.unknown("오늘 진료시간 확인 필요");
		}
		OpenRange range = detail.hours().days().get(day);
		if (range == null) {
			if (sundayNote == NoteKind.OPEN) {
				return OpenStatus.unknown("일요일 진료시간 확인 필요");
			}
			return OpenStatus.closed("오늘 휴진");
		}
		if (sundayNote == NoteKind.CLOSED) {
			return OpenStatus.unknown("일요일 진료 여부 확인 필요");
		}
		int minute = now.getHour() * 60 + now.getMinute();
		if (minute < range.open()) {
			return OpenStatus.closed("진료 전");
		}
		if (minute >= range.close()) {
			return OpenStatus.closed("진료 끝");
		}
		if (!holiday && day != DayOfWeek.SUNDAY) {
			Lunch lunch = day == DayOfWeek.SATURDAY ? detail.lunchSat() : detail.lunchWeek();
			if (lunch.contains(minute)) {
				return OpenStatus.closed("점심시간");
			}
			if (lunch.kind() == LunchKind.UNKNOWN && minute >= LUNCH_GUESS_START && minute < LUNCH_GUESS_END) {
				return OpenStatus.unknown("점심시간 확인 필요");
			}
		}
		return OpenStatus.OPEN;
	}

}
