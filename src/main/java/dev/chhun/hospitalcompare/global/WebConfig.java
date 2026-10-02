package dev.chhun.hospitalcompare.global;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

	// jackson-dataformat-xml은 심평원 응답 파싱용인데, 클래스패스에 있으면 MVC도 XML로 응답할 수 있게 된다.
	// 브라우저 Accept(application/xml;q=0.9)에 XML로 답하지 않도록 Accept 헤더를 보지 않고 JSON으로 응답한다.
	@Override
	public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
		configurer.ignoreAcceptHeader(true).defaultContentType(MediaType.APPLICATION_JSON);
	}

}
