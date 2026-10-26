package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 입력은 2026-10-05 상세정보서비스 2.8 무작위 병원급 200곳 응답에서 본 값이다. */
class ClosedNoteClassifierTest {

	@ParameterizedTest
	@ValueSource(strings = {"전부 휴진", "전부휴진", "휴진", "휴무", "전부휴진(양방외래,한방외래,병동24시간근무)",
			"전부 휴진 (응급실만 운영)", "휴진(응급실 미운영)"})
	void 휴진(String text) {
		assertThat(ClosedNoteClassifier.classify(text)).isEqualTo(new Note(NoteKind.CLOSED, text));
	}

	@ParameterizedTest
	@ValueSource(strings = {"정상진료", "24시간 진료", "없음(24시간 진료)"})
	void 진료(String text) {
		assertThat(ClosedNoteClassifier.classify(text)).isEqualTo(new Note(NoteKind.OPEN, text));
	}

	@ParameterizedTest
	@ValueSource(strings = {"휴진(설날, 근로자의 날, 추석 등 일부 진료)", "오후 4시 이후", "개별공지", "-",
			"산부인과: 당직의 09:00 ~ 13:00 진료/ 소아과, 내과, 유방외과 휴진", "응급의학과 전문의 7인 24시간 상주"})
	void 그_밖은_모름(String text) {
		assertThat(ClosedNoteClassifier.classify(text)).isEqualTo(new Note(NoteKind.UNKNOWN, text));
	}

	@Test
	void 원문이_없으면_분류도_없다() {
		assertThat(ClosedNoteClassifier.classify(null)).isEqualTo(Note.EMPTY);
		assertThat(ClosedNoteClassifier.classify("  ")).isEqualTo(Note.EMPTY);
	}

}
