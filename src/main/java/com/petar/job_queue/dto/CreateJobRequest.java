package com.petar.job_queue.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateJobRequest(String type, JsonNode payload) {
}
