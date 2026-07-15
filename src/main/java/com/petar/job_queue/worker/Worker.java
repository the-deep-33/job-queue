package com.petar.job_queue.worker;

import com.petar.job_queue.handler_registry.HandlerRegistry;
import com.petar.job_queue.handlers.JobHandler;
import com.petar.job_queue.model.JobRecord;
import com.petar.job_queue.repository.JobRepository;

public class Worker implements Runnable{

    private final HandlerRegistry registry;
    private final JobRepository jobRepository;
    private final String worker_id;

    public Worker(HandlerRegistry registry, JobRepository jobRepository, String worker_id) {
        this.registry = registry;
        this.jobRepository = jobRepository;
        this.worker_id = worker_id;
    }

    @Override
    public void run() {
        while(true)
        {
            JobRecord new_job = jobRepository.claim(worker_id);
            if(new_job != null)
            {
                JobHandler job_handler = registry.get(new_job.type());
                if(job_handler == null)
                {
                    jobRepository.markDead(new_job.id(), "Job type is nonexistent");
                }
                else{
                    try {
                        job_handler.handle(new_job.payload());
                        jobRepository.markSucceeded(new_job.id());
                    } catch (Exception e) {
                        jobRepository.markDead(new_job.id(), e.toString());
                    }
                }
            }
            else{
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
