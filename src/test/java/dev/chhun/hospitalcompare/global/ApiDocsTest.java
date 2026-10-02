package dev.chhun.hospitalcompare.global;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
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
