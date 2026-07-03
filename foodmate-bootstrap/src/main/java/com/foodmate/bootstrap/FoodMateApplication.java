package com.foodmate.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FoodMate 后端启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.foodmate")
public class FoodMateApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodMateApplication.class, args);
    }
}
