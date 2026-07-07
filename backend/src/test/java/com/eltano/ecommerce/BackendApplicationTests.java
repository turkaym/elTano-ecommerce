package com.eltano.ecommerce;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void schedulingIsEnabledForScheduledCatalogWorkers() {
		assertTrue(BackendApplication.class.isAnnotationPresent(EnableScheduling.class));
	}

}
