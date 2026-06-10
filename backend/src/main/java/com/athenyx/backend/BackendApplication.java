package com.athenyx.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boot entry point for the Athenyx Ward backend.
 *
 * <p>Starts the embedded Spring Boot container, which wires up the REST API,
 * OAuth2/JWT security, JPA repositories, and the Gmail API client.</p>
 */
@SpringBootApplication
public class BackendApplication {

	/**
	 * Launches the Spring Boot application.
	 *
	 * @param args standard command-line arguments forwarded to Spring
	 */
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
