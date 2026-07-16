package com.petar.job_queue.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BulkTestHandler implements JobHandler{

    private final Map<String, AtomicInteger> job_execution_count = new ConcurrentHashMap<>();

    @Override
    public void handle(JsonNode payload) throws Exception{
        String payload_str = payload.get("index").asText();
        job_execution_count.putIfAbsent(payload_str, new AtomicInteger());
        job_execution_count.get(payload_str).incrementAndGet();
    }

    @Override
    public String type() {
        return "bulk-test";
    }

    public Map<String, AtomicInteger> getJob_execution_count() {
        return job_execution_count;
    }

}
