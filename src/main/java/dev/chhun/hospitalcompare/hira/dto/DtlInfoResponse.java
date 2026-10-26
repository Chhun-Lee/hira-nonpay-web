package dev.chhun.hospitalcompare.hira.dto;

import java.util.List;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

/** 세부정보 응답의 XML 구조 */
public record DtlInfoResponse(HiraHeader header, Body body) implements HiraResponse {

	public record Body(Items items, int numOfRows, int pageNo, int totalCount) {
	}

	/** 세부정보가 없는 기관은 {@code <items/>}로 온다. */
	public record Items(@JacksonXmlElementWrapper(useWrapping = false) List<DtlInfoItem> item) {
	}

	@Override
	public boolean hasBody() {
		return body != null;
	}

	public DetailPage<DtlInfoItem> toPage() {
		List<DtlInfoItem> items = body.items() == null || body.items().item() == null
				? List.of()
				: body.items().item();
		return new DetailPage<>(items, body.totalCount());
	}

}
