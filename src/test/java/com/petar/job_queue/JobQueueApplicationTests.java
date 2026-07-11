package com.petar.job_queue;

import com.petar.job_queue.dto.CreateJobRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobQueueApplicationTests {

	@Autowired
	private JdbcTemplate jdbc_template;

	@Autowired
	private TestRestTemplate test_template;

	@Test
	void contextLoads() {
	}

	@Test
	void postRequestTest()
	{

        try {
			ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree("{\"to\": \"someone\", \"subject\": \"Hey\"}");
			CreateJobRequest req = new CreateJobRequest("send-email", payload);

			ResponseEntity resp = test_template.postForEntity("/jobs", req, Map.class);

			assertThat(resp.getStatusCode().value()).isEqualTo(201);

			int count = jdbc_template.queryForObject(
					"SELECT COUNT(*) FROM JOBS",
					Integer.class
			);

			assertThat(count).isEqualTo(1);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
