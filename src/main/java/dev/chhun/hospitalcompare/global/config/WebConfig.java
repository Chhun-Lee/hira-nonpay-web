package dev.chhun.hospitalcompare.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.xml.JacksonXmlHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

	// jackson-dataformat-xml은 심평원 응답 파싱용인데, 클래스패스에 있으면 MVC가 XML 응답 변환기도 등록한다.
	// 그러면 브라우저 Accept(application/xml;q=0.9)에 XML로 답하므로 MVC 변환기 목록에서만 뺀다.
	@Override
	public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
		builder.configureMessageConvertersList(converters ->
				converters.removeIf(converter -> converter instanceof JacksonXmlHttpMessageConverter));
	}

}
