package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.entity.LunchKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 입력은 2026-10-05 상세정보서비스 2.8 무작위 병원급 200곳 응답에서 본 값이다. */
class LunchParserTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"12시30분~13시30분|750|810",
			"12:30~13:30|750|810",
			"12:30 ~ 13:30|750|810",
			"12시 30분~13시 30분|750|810",
			"13:00-14:00|780|840",
			"12시00분 ~ 13시:30분|720|810",
			"13 :00 ~ 14: 00|780|840",
			"12시 20분 ~ 13시 20분|740|800",
			"12:30 - 13:30|750|810",
			"12시30분~1시30분|750|810",
			"12시30분~2시00분|750|840",
			"13시~14시|780|840",
			"12:00～13:00|720|780"})
	void 시각_두_개를_이은_범위_하나를_읽는다(String text, int start, int end) {
		Lunch lunch = LunchParser.parse(text);

		assertThat(lunch.kind()).isEqualTo(LunchKind.RANGE);
		assertThat(lunch.start()).isEqualTo(start);
		assertThat(lunch.end()).isEqualTo(end);
		assertThat(lunch.text()).isEqualTo(text);
	}

	@ParameterizedTest
	@ValueSource(strings = {"없음", "휴진", " 없음 "})
	void 없음과_휴진은_점심_없음이다(String text) {
		Lunch lunch = LunchParser.parse(text);

		assertThat(lunch.kind()).isEqualTo(LunchKind.NONE);
		assertThat(lunch.text()).isEqualTo(text);
	}

	@Test
	void 원문이_없으면_점심_없음이고_원문도_null이다() {
		assertThat(LunchParser.parse(null)).isEqualTo(Lunch.none(null));
		assertThat(LunchParser.parse(" ")).isEqualTo(Lunch.none(null));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"점심 : 13시00분 ~ 14시00분, 저녁 : 18시00분 ~ 19시00분",
			"(월,화,목,금) 12시~13시",
			"12시30분",
			"13시30분~12시30분",
			"11시~15시",
			"1230~1330",
			"홈페이지 참고"})
	void 범위_하나로_읽지_못하면_모름이다(String text) {
		Lunch lunch = LunchParser.parse(text);

		assertThat(lunch.kind()).isEqualTo(LunchKind.UNKNOWN);
		assertThat(lunch.start()).isNull();
		assertThat(lunch.text()).isEqualTo(text);
	}

}
