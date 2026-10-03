package dev.chhun.hospitalcompare.hira.dto;

import java.util.List;
import java.util.Map;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

/**
 * 종별·시도별 통계 응답의 XML 구조. 항목마다 칸이 수십 개이고 값이 없는 칸은 빠져서 오므로, 칸 이름 → 값 맵으로 받는다.
 */
public record NonpayStatListResponse(HiraHeader header, Body body) implements HiraResponse {

	public record Body(Items items, int numOfRows, int pageNo, int totalCount) {
	}

	public record Items(@JacksonXmlElementWrapper(useWrapping = false) List<Map<String, String>> item) {
	}

	@Override
	public boolean hasBody() {
		return body != null;
	}

	public NonpayPage<Map<String, String>> toPage() {
		List<Map<String, String>> items = body.items() == null || body.items().item() == null
				? List.of()
				: body.items().item();
		return new NonpayPage<>(items, body.numOfRows(), body.pageNo(), body.totalCount());
	}

}
