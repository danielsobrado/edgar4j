package org.jds.edgar4j.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:worker-mobile-assist.yml", factory = YamlPropertySourceFactory.class)
public class WorkerMobileAssistDefaultsConfiguration {
}
