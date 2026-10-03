package dev.chhun.hospitalcompare.collector.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** 비급여 응답 문자열 값 변환 */
final class NonpayValues {

	/** 2월 30일 같은 날짜와 뒤에 붙은 오프셋(Z 등)을 거절하도록 엄격하게 읽는다. */
	private static final DateTimeFormatter YYYYMMDD =
			DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

	private NonpayValues() {
	}

	static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/** 원 단위 금액. 비어 있거나 숫자가 아니면 null. 소수는 반올림한다. */
	static Long amount(String raw) {
		if (isBlank(raw)) {
			return null;
		}
		try {
			return new BigDecimal(raw.trim()).setScale(0, RoundingMode.HALF_UP).longValueExact();
		} catch (NumberFormatException | ArithmeticException e) {
			return null;
		}
	}

	/** yyyyMMdd. 비어 있으면 null, 형식이 틀리면 DateTimeParseException */
	static LocalDate date(String raw) {
		if (isBlank(raw)) {
			return null;
		}
		return LocalDate.parse(raw.trim(), YYYYMMDD);
	}

	/** 형식이 틀린 날짜를 확인할 때 쓴다. */
	static boolean isValidDate(String raw) {
		try {
			date(raw);
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}

}
