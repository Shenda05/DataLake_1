package com.datalake.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DataLakePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataLakePlatformApplication.class, args);
    }
}
