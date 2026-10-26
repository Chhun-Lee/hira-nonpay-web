package dev.chhun.hospitalcompare.detail.dto;

import dev.chhun.hospitalcompare.detail.entity.LunchKind;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

/**
 * 세부정보 화면용. 시각은 "08:30" 문자열로 준다.
 *
 * @param hours 월~일 7개. 진료 시각이 하나도 없는 기관이면 빈 목록
 */
public record DetailView(
		List<DayView> hours,
		LunchView lunchWeek,
		LunchView lunchSat,
		String receptionWeek,
		String receptionSat,
		Note sundayNote,
		Note holidayNote,
		Boolean emergencyDay,
		Boolean emergencyNight,
		ParkingView parking) {

	public enum DayState {

		/** 시각이 있다 */
		OPEN_DAY,

		/** 그 요일 칸이 없다(휴진) */
		CLOSED_DAY,

		/** 시각 칸은 있었지만 읽지 못했다 */
		UNKNOWN_DAY

	}

	/** @param close 종료. 자정이면 "24:00" */
	public record DayView(DayOfWeek day, DayState state, String open, String close) {
	}

	public record LunchView(LunchKind kind, String start, String end, String text) {
	}

	/** @param fee 주차비 부담 여부. 모르면 null */
	public record ParkingView(Integer capacity, Boolean fee, String note) {
	}

	public static DetailView of(HospitalDetailRecord record) {
		WeeklyHours weekly = record.hours();
		List<DayView> hours = weekly.hasAny()
				? Arrays.stream(DayOfWeek.values()).map(day -> day(weekly, day)).toList()
				: List.of();
		return new DetailView(hours, lunch(record.lunchWeek()), lunch(record.lunchSat()), record.receptionWeek(),
				record.receptionSat(), record.sundayNote(), record.holidayNote(), record.emergencyDay(),
				record.emergencyNight(),
				new ParkingView(record.parkingCapacity(), record.parkingFee(), record.parkingNote()));
	}

	private static DayView day(WeeklyHours weekly, DayOfWeek day) {
		OpenRange range = weekly.days().get(day);
		if (range != null) {
			return new DayView(day, DayState.OPEN_DAY, time(range.open()), time(range.close()));
		}
		if (weekly.unknownDays().contains(day)) {
			return new DayView(day, DayState.UNKNOWN_DAY, null, null);
		}
		return new DayView(day, DayState.CLOSED_DAY, null, null);
	}

	private static LunchView lunch(Lunch lunch) {
		return new LunchView(lunch.kind(), lunch.start() == null ? null : time(lunch.start()),
				lunch.end() == null ? null : time(lunch.end()), lunch.text());
	}

	/** 자정부터 센 분 → "HH:mm". 1440은 "24:00"이다 */
	static String time(int minutes) {
		return "%02d:%02d".formatted(minutes / 60, minutes % 60);
	}

}
