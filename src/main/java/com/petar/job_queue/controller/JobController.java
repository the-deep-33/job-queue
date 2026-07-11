package com.petar.job_queue.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.petar.job_queue.dto.CreateJobRequest;
import com.petar.job_queue.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class JobController {

    private final JobService jobService;

    @Autowired
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/jobs")
    public ResponseEntity createJob(@RequestBody CreateJobRequest request)
    {
        UUID id = jobService.handleJobRequest(request);
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

}
