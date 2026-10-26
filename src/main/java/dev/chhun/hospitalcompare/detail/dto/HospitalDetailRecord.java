package dev.chhun.hospitalcompare.detail.dto;

/**
 * 한 기관의 세부정보(해석한 값과 원문). 수집기가 적재하고 판정·상세 화면이 읽는다.
 * hours·lunch·note가 null이면 "없음" 값으로 바꾼다.
 *
 * @param receptionWeek   평일 접수 시간 원문(해석하지 않는다)
 * @param emergencyDay    주간 응급실 운영. 모르면 null
 * @param parkingCapacity 주차 가능 대수. 모르면 null
 * @param parkingFee      주차비 부담 여부(parkXpnsYn). 모르면 null
 */
public record HospitalDetailRecord(
		String ykiho,
		WeeklyHours hours,
		Lunch lunchWeek,
		Lunch lunchSat,
		String receptionWeek,
		String receptionSat,
		Note sundayNote,
		Note holidayNote,
		Boolean emergencyDay,
		Boolean emergencyNight,
		Integer parkingCapacity,
		Boolean parkingFee,
		String parkingNote) {

	public HospitalDetailRecord {
		hours = hours == null ? WeeklyHours.NONE : hours;
		lunchWeek = lunchWeek == null ? Lunch.none(null) : lunchWeek;
		lunchSat = lunchSat == null ? Lunch.none(null) : lunchSat;
		sundayNote = sundayNote == null ? Note.EMPTY : sundayNote;
		holidayNote = holidayNote == null ? Note.EMPTY : holidayNote;
	}

}
