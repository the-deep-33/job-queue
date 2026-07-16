package com.petar.job_queue.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SendEmailHandler implements JobHandler {

    @Override
    public void handle(JsonNode payload) throws Exception{

    }

    @Override
    public String type() {
        return "send-email";
    }
}
