package com.petar.job_queue.repository;

import com.petar.job_queue.dto.CreateJobRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JobRepository {

    private final JdbcTemplate template;

    @Autowired
    public JobRepository(JdbcTemplate template) {
        this.template = template;
    }

    public UUID insertJobRow(CreateJobRequest jobRequest)
    {
        String query = "INSERT INTO jobs(type, payload) VALUES(?, ?::jsonb) RETURNING id";
        UUID id = template.queryForObject(
                query,
                UUID.class,
                jobRequest.type(),
                jobRequest.payload().toString()
        );
        return id;
    }
}
