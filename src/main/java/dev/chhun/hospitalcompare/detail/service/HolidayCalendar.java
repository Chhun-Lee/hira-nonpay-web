package dev.chhun.hospitalcompare.detail.service;

import dev.chhun.hospitalcompare.detail.config.HoursProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 설정(hours.holidays)의 공휴일. 목록이 올해를 덮지 못하면 공휴일도 평일처럼 판정되므로 기동할 때 경고한다. */
@Component
public class HolidayCalendar {

	private static final Logger log = LoggerFactory.getLogger(HolidayCalendar.class);

	private final Set<LocalDate> holidays;

	public HolidayCalendar(HoursProperties properties, Clock clock) {
		this.holidays = properties.holidayDates();
		int year = LocalDate.now(clock).getYear();
		if (holidays.stream().noneMatch(date -> date.getYear() == year)) {
			log.warn("공휴일 목록(hours.holidays)에 {}년 날짜가 없습니다. 공휴일도 평일처럼 판정합니다", year);
		}
	}

	public boolean isHoliday(LocalDate date) {
		return holidays.contains(date);
	}

}
