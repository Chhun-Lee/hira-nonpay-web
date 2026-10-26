package dev.chhun.hospitalcompare.detail.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 상세정보 설정. 수집기(대상)와 조회 서버(병원급 판단)가 함께 쓴다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HospitalLevelProperties.class)
class DetailConfig {
}
