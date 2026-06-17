package com.study.grabthisforme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GrabThisForMeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrabThisForMeApplication.class, args);
    }

}
