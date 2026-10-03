package dev.chhun.hospitalcompare.hira.dto;

import java.util.List;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

/**
 * 루트가 {@code <response>}인 목록 응답의 XML 구조. 오류여도 header는 온다.
 */
public record HospBasisListResponse(HiraHeader header, Body body) implements HiraResponse {

	public record Body(Items items, int numOfRows, int pageNo, int totalCount) {
	}

	/** {@code <items><item/>...</items>}. 결과가 0건이면 {@code <items/>}로 온다. */
	public record Items(@JacksonXmlElementWrapper(useWrapping = false) List<HospBasisItem> item) {
	}

	@Override
	public boolean hasBody() {
		return body != null;
	}

	public HospBasisPage toPage() {
		List<HospBasisItem> items = body.items() == null || body.items().item() == null
				? List.of()
				: body.items().item();
		return new HospBasisPage(items, body.numOfRows(), body.pageNo(), body.totalCount());
	}

}
