package dev.chhun.hospitalcompare.global;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@ApiIntegrationTest
class ApiDocsTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void OpenAPI_문서에_조회_API가_있다() {
		assertThat(mvc.get().uri("/v3/api-docs"))
				.hasStatusOk()
				.bodyJson().extractingPath("$.paths").asMap()
				.containsKeys("/api/hospitals", "/api/meta/snapshot");
	}

	@Test
	void 스웨거_UI가_뜬다() {
		assertThat(mvc.get().uri("/swagger-ui/index.html"))
				.hasStatusOk();
	}

}
