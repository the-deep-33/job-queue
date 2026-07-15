package com.petar.job_queue.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petar.job_queue.dto.CreateJobRequest;
import com.petar.job_queue.model.JobRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JobRepository {

    private final JdbcTemplate template;
    private final ObjectMapper mapper;

    @Autowired
    public JobRepository(JdbcTemplate template, ObjectMapper mapper) {
        this.template = template;
        this.mapper = mapper;
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

    public JobRecord claim(String worker_id)
    {
        String query = "UPDATE jobs SET worker_id = ?, status = 'running' WHERE id = (SELECT id FROM jobs " +
                "WHERE status='pending' ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING id, type, payload";

        return template.query(
                query, rs -> {
                    if(rs.next())
                    {
                        UUID id = rs.getObject("id", UUID.class);
                        String type = rs.getString("type");
                        String payload_str = rs.getString("payload");
                        JsonNode payload = null;
                        try {
                            payload = mapper.readTree(payload_str);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                        JobRecord job = new JobRecord(id, type, payload);
                        return job;
                    }
                    return null;
                }, worker_id
        );
    }

    public void markSucceeded(UUID job_id)
    {
        String query = "UPDATE jobs SET status = 'succeeded' WHERE id = ?";
        template.update(query, job_id);
    }

    public void markDead(UUID job_id, String last_error)
    {
        String query = "UPDATE jobs SET status = 'dead', last_error = ? WHERE id = ?";
        template.update(query, last_error, job_id);
    }

}
