package dev.chhun.hospitalcompare.hira.dto;

import java.time.DayOfWeek;

/**
 * 세부정보(getDtlInfo2.8)의 item. 검증·해석 전이라 모두 문자열로 받는다. 값이 없는 칸은 응답에서 빠진다(null).
 * 위치 안내(plcNm 등)와 응급실 전화번호는 쓰지 않아 받지 않는다(모르는 칸은 무시된다).
 *
 * @param lunchWeek  월~금 점심시간(자유 문자열)
 * @param rcvWeek    평일 접수시간(자유 문자열)
 * @param noTrmtSun  일요일 휴진 안내(자유 문자열)
 * @param noTrmtHoli 공휴일 휴진 안내(자유 문자열)
 * @param parkXpnsYn 주차비용 부담 여부(Y/N)
 */
public record DtlInfoItem(
		String trmtMonStart, String trmtMonEnd,
		String trmtTueStart, String trmtTueEnd,
		String trmtWedStart, String trmtWedEnd,
		String trmtThuStart, String trmtThuEnd,
		String trmtFriStart, String trmtFriEnd,
		String trmtSatStart, String trmtSatEnd,
		String trmtSunStart, String trmtSunEnd,
		String lunchWeek, String lunchSat,
		String rcvWeek, String rcvSat,
		String noTrmtSun, String noTrmtHoli,
		String emyDayYn, String emyNgtYn,
		String parkQty, String parkXpnsYn, String parkEtc) {

	/** 그 요일 진료 시작(HHmm) */
	public String start(DayOfWeek day) {
		return switch (day) {
			case MONDAY -> trmtMonStart;
			case TUESDAY -> trmtTueStart;
			case WEDNESDAY -> trmtWedStart;
			case THURSDAY -> trmtThuStart;
			case FRIDAY -> trmtFriStart;
			case SATURDAY -> trmtSatStart;
			case SUNDAY -> trmtSunStart;
		};
	}

	/** 그 요일 진료 종료(HHmm) */
	public String end(DayOfWeek day) {
		return switch (day) {
			case MONDAY -> trmtMonEnd;
			case TUESDAY -> trmtTueEnd;
			case WEDNESDAY -> trmtWedEnd;
			case THURSDAY -> trmtThuEnd;
			case FRIDAY -> trmtFriEnd;
			case SATURDAY -> trmtSatEnd;
			case SUNDAY -> trmtSunEnd;
		};
	}

}
