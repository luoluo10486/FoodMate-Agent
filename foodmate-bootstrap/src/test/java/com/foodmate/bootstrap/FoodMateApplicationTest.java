package com.foodmate.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.profiles.active=local-stub")
class FoodMateApplicationTest {
    @Test
    void contextLoadsWithLocalStubProfile() {
    }
}

