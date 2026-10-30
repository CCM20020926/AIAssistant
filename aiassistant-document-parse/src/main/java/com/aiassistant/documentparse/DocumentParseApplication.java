package com.aiassistant.documentparse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DocumentParseApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentParseApplication.class, args);
    }
}
