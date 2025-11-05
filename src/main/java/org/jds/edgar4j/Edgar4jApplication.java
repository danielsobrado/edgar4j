package org.jds.edgar4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Edgar4jApplication {

	/**
	 * @author J. Daniel Sobrado
	 * @version 1.0
	 * @since 2022-09-18
	 */
	public static void main(String[] args) {
		SpringApplication.run(Edgar4jApplication.class, args);
	}

}
