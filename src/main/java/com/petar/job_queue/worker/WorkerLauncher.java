package com.petar.job_queue.worker;

import com.petar.job_queue.handler_registry.HandlerRegistry;
import com.petar.job_queue.repository.JobRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;

@Component
public class WorkerLauncher implements ApplicationRunner {

    private final JobRepository jobRepository;
    private final HandlerRegistry handlerRegistry;

    public WorkerLauncher(JobRepository jobRepository, HandlerRegistry handlerRegistry) {
        this.jobRepository = jobRepository;
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int worker_number = 10;
        String host_name = Inet4Address.getLocalHost().getCanonicalHostName();
        for(int i = 0; i < worker_number; ++i)
        {
            String worker_id = host_name + "-" + i;
            Worker worker_thread = new Worker(handlerRegistry, jobRepository, worker_id);
            new Thread(worker_thread).start();
        }
    }
}
