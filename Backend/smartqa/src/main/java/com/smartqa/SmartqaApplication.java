package com.smartqa;

import com.smartqa.common.config.DotEnvLoader;
import com.smartqa.common.config.SmartQaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SmartQaProperties.class)
public class SmartqaApplication {

	public static void main(String[] args) {
		DotEnvLoader.loadIfPresent();
		SpringApplication.run(SmartqaApplication.class, args);
	}

}
