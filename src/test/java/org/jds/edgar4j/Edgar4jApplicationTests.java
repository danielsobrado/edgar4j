package org.jds.edgar4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class Edgar4jApplicationTests {

	@Test
	void contextLoads() {
	}

}
