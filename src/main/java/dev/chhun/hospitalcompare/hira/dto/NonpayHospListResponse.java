package dev.chhun.hospitalcompare.hira.dto;

import java.util.List;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

/** 항목별 병원 목록 요약 응답의 XML 구조 */
public record NonpayHospListResponse(HiraHeader header, Body body) implements HiraResponse {

	public record Body(Items items, int numOfRows, int pageNo, int totalCount) {
	}

	/** 결과가 0건이면 {@code <items/>}로 온다. */
	public record Items(@JacksonXmlElementWrapper(useWrapping = false) List<NonpayHospPrice> item) {
	}

	@Override
	public boolean hasBody() {
		return body != null;
	}

	public NonpayPage<NonpayHospPrice> toPage() {
		List<NonpayHospPrice> items = body.items() == null || body.items().item() == null
				? List.of()
				: body.items().item();
		return new NonpayPage<>(items, body.numOfRows(), body.pageNo(), body.totalCount());
	}

}
