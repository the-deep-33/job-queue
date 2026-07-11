package com.petar.job_queue.service;

import com.petar.job_queue.dto.CreateJobRequest;
import com.petar.job_queue.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JobService {

    private final JobRepository repo;

    @Autowired
    public JobService(JobRepository repo) {
        this.repo = repo;
    }

    public UUID handleJobRequest(CreateJobRequest jobRequest)
    {
        return repo.insertJobRow(jobRequest);
    }

}
