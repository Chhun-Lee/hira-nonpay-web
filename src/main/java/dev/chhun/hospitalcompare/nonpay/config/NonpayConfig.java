package dev.chhun.hospitalcompare.nonpay.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 비급여 조회 서비스는 프로필과 관계없이 뜨므로 설정도 프로필 없이 올린다(값은 application-api.yml). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NonpayUiProperties.class)
class NonpayConfig {
}
