package com.petar.job_queue.handlers;

import com.fasterxml.jackson.databind.JsonNode;

public interface JobHandler {

    void handle(JsonNode payload) throws Exception;
    String type();

}
