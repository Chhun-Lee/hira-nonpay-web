package dev.chhun.hospitalcompare.collector.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("collector")
@EnableConfigurationProperties(CollectorProperties.class)
class CollectorConfig {
}
