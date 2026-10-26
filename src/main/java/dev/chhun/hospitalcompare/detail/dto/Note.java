package dev.chhun.hospitalcompare.detail.dto;

import dev.chhun.hospitalcompare.detail.entity.NoteKind;

/**
 * 일요일·공휴일 휴진 안내. 원문이 없으면 kind와 text 모두 null이다.
 */
public record Note(NoteKind kind, String text) {

	public static final Note EMPTY = new Note(null, null);

}
