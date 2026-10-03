package dev.chhun.hospitalcompare.hira.dto;

/** 루트가 {@code <response>}인 심평원 응답. 공통 호출부가 header로 성공 여부를 판단한다. */
public interface HiraResponse {

	HiraHeader header();

	/** body가 있는지. 오류 응답은 header만 오기도 한다. */
	boolean hasBody();

}
