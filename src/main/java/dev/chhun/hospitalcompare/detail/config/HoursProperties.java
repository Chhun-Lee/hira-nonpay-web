package dev.chhun.hospitalcompare.detail.config;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 지금 진료 중 판정 설정.
 *
 * @param holidays 관공서 공휴일(yyyy-MM-dd). 대체·임시 공휴일 포함. 새 API를 쓰지 않고 연 1회 손으로 갱신한다(plan.md 결정)
 */
@ConfigurationProperties("hours")
public record HoursProperties(@DefaultValue List<String> holidays) {

	/** @throws IllegalStateException 날짜(yyyy-MM-dd)가 아닌 값이 있으면 */
	public Set<LocalDate> holidayDates() {
		Set<LocalDate> dates = new HashSet<>();
		for (String value : holidays) {
			try {
				dates.add(LocalDate.parse(value.strip()));
			} catch (DateTimeParseException e) {
				throw new IllegalStateException("hours.holidays에 날짜(yyyy-MM-dd)가 아닌 값이 있습니다: " + value);
			}
		}
		return Set.copyOf(dates);
	}

}
