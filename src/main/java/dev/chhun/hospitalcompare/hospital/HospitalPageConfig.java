package dev.chhun.hospitalcompare.hospital;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 화면은 api 서버만 그린다. collector에는 카카오 키가 필요 없다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("api")
@EnableConfigurationProperties(KakaoMapProperties.class)
class HospitalPageConfig {
}
