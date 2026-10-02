package dev.chhun.hospitalcompare;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * api 프로파일 통합 테스트. 이 어노테이션을 쓰는 테스트는 Spring 컨텍스트와 MySQL 컨테이너를 공유한다.
 * 데이터는 각 테스트가 @BeforeEach에서 지우고 다시 넣는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "kakao.map.js-key=test-kakao-key")
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
public @interface ApiIntegrationTest {
}
