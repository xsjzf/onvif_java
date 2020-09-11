package com.onvif.java;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author zf
 */
@SpringBootApplication
public class JavaOnvifApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(JavaOnvifApplication.class, args);
    }


    @Override
    public void run(String... args) throws Exception {
    }
}
