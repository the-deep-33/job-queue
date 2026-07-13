package com.petar.job_queue.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record JobRecord(UUID id, String type, JsonNode payload) {
}
