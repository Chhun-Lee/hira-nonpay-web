package dev.chhun.hospitalcompare.hira.dto;

import java.util.List;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

/** 진료과목정보 응답의 XML 구조 */
public record SubjectListResponse(HiraHeader header, Body body) implements HiraResponse {

	public record Body(Items items, int numOfRows, int pageNo, int totalCount) {
	}

	public record Items(@JacksonXmlElementWrapper(useWrapping = false) List<SubjectItem> item) {
	}

	@Override
	public boolean hasBody() {
		return body != null;
	}

	public DetailPage<SubjectItem> toPage() {
		List<SubjectItem> items = body.items() == null || body.items().item() == null
				? List.of()
				: body.items().item();
		return new DetailPage<>(items, body.totalCount());
	}

}
