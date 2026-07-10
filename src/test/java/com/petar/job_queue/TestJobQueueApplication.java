package com.petar.job_queue;

import org.springframework.boot.SpringApplication;

public class TestJobQueueApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobQueueApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
