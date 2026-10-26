package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import java.util.Set;

/**
 * 일요일·공휴일 휴진 안내 원문을 휴진·진료·모름으로 나눈다. 공백을 모두 지운 값으로 판단한다.
 * 휴진: 휴진·휴무·전부휴진·전부휴무, "전부휴진(…)", 괄호 안에 "진료"가 없는 "휴진(…)".
 * 진료: 정상진료, 24시간진료, 없음(24시간진료). 나머지는 모름이다.
 */
final class ClosedNoteClassifier {

	private static final Set<String> CLOSED = Set.of("휴진", "휴무", "전부휴진", "전부휴무");
	private static final Set<String> OPEN = Set.of("정상진료", "24시간진료", "없음(24시간진료)");

	private ClosedNoteClassifier() {
	}

	static Note classify(String text) {
		if (text == null || text.isBlank()) {
			return Note.EMPTY;
		}
		String compact = text.replaceAll("\\s+", "");
		if (CLOSED.contains(compact) || compact.startsWith("전부휴진(")
				|| (compact.startsWith("휴진(") && !compact.substring("휴진".length()).contains("진료"))) {
			return new Note(NoteKind.CLOSED, text);
		}
		if (OPEN.contains(compact)) {
			return new Note(NoteKind.OPEN, text);
		}
		return new Note(NoteKind.UNKNOWN, text);
	}

}
