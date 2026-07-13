package com.petar.job_queue.worker;

import com.petar.job_queue.handler_registry.HandlerRegistry;
import com.petar.job_queue.handlers.JobHandler;
import com.petar.job_queue.repository.JobRepository;

public class Worker implements Runnable{

    private final HandlerRegistry registry;
    private final JobRepository jobRepository;

    public Worker(HandlerRegistry registry, JobRepository jobRepository) {
        this.registry = registry;
        this.jobRepository = jobRepository;
    }

    @Override
    public void run() {
        while(true)
        {

        }
    }
}
