package com.chinmayshivratriwar.nl_sql_engine;

import com.ares.sdk.EnableAresMonitoring;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAresMonitoring
@EnableScheduling
@SpringBootApplication
public class NlSqlEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(NlSqlEngineApplication.class, args);
	}

}
