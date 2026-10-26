package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.collector.service.DetailHoursParser.DayResult;
import dev.chhun.hospitalcompare.collector.service.DetailHoursParser.Outcome;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 입력은 상세정보서비스 2.8 실제 응답에서 본 값이다(병원급 2026-10-05, 의원급 2026-10-26). */
class DetailHoursParserTest {

	@Test
	void 시작과_종료를_자정부터_센_분으로_읽는다() {
		DayResult result = DetailHoursParser.parse("0830", "1700");

		assertThat(result.outcome()).isEqualTo(Outcome.VALID);
		assertThat(result.range()).isEqualTo(new OpenRange(510, 1020));
	}

	@Test
	void 종료_2400과_0000은_자정이다() {
		assertThat(DetailHoursParser.parse("0900", "2400").range()).isEqualTo(new OpenRange(540, 1440));
		assertThat(DetailHoursParser.parse("0900", "0000").range()).isEqualTo(new OpenRange(540, 1440));
	}

	@Test
	void 영시부터_2400은_24시간이다() {
		assertThat(DetailHoursParser.parse("0000", "2400").range()).isEqualTo(new OpenRange(0, 1440));
	}

	/**
	 * 0900~0100은 자정 넘김인지 12시간제(13시)인지, 0000~0000은 24시간인지 휴진 요일 자리채움인지 모른다.
	 * 0900~0630·0900~0300은 의원급 표본에서 12시간제로 쓴 값이다(18:30, 15:00).
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"0900|0100",
			"0900|0630",
			"0900|0300",
			"0000|0000",
			"0900|0900",
			"2400|2400"})
	void 종료가_시작보다_이르거나_같으면_모름이다(String start, String end) {
		DayResult result = DetailHoursParser.parse(start, end);

		assertThat(result.outcome()).isEqualTo(Outcome.AMBIGUOUS);
		assertThat(result.range()).isNull();
	}

	@Test
	void 두_칸_모두_없으면_칸_없음이다() {
		assertThat(DetailHoursParser.parse(null, null).outcome()).isEqualTo(Outcome.ABSENT);
		assertThat(DetailHoursParser.parse(" ", "").outcome()).isEqualTo(Outcome.ABSENT);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "NULL", value = {
			"0830|NULL",
			"NULL|1700",
			"830|1700",
			"08:30|17:00",
			"0860|1700",
			"2500|0100",
			"2410|0100",
			"오전|1700"})
	void 한_칸만_있거나_형식이_틀리면_모름이다(String start, String end) {
		DayResult result = DetailHoursParser.parse(start, end);

		assertThat(result.outcome()).isEqualTo(Outcome.INVALID);
		assertThat(result.range()).isNull();
	}

	@Test
	void 앞뒤_공백은_무시한다() {
		assertThat(DetailHoursParser.minutes(" 0930 ")).isEqualTo(570);
	}

}
