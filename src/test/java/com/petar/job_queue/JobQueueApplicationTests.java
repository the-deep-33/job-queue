package com.petar.job_queue;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobQueueApplicationTests {

	@Autowired
	private JdbcTemplate jdbc_template;

	@Autowired
	private TestRestTemplate test_template;

	@Autowired
	private ObjectMapper mapper;

	@Test
	void contextLoads() {
	}

	@Test
	void postRequestTest()
	{

        try {

            JsonNode payload = mapper.readTree("{\"to\": \"someone\", \"subject\": \"Hey\"}");
			CreateJobRequest req = new CreateJobRequest("send-email", payload);

			ResponseEntity<Map> resp = test_template.postForEntity("/jobs", req, Map.class);

			assertThat(resp.getStatusCode().value()).isEqualTo(201);

			Map body = resp.getBody();

			UUID job_id = UUID.fromString((String) body.get("id"));

			int count = jdbc_template.queryForObject(
					"SELECT COUNT(*) FROM JOBS WHERE id = ?",
					Integer.class,
					job_id
			);

			assertThat(count).isEqualTo(1);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	@Test
	void workerFetchTest() throws Exception {
		JsonNode payload = mapper.readTree("{\"to\": \"someone\", \"subject\": \"Hey\"}");
		CreateJobRequest req = new CreateJobRequest("send-email", payload);

		ResponseEntity<Map> resp = test_template.postForEntity("/jobs", req, Map.class);
		assertThat(resp.getStatusCode().value()).isEqualTo(201);

		Map body = resp.getBody();
		UUID job_id = UUID.fromString((String) body.get("id"));

		int max_tries = 10;
		boolean success = false;

		for(int i = 0; i < max_tries; ++i)
		{
			int count = jdbc_template.queryForObject(
					"SELECT COUNT(*) FROM JOBS WHERE id = ? AND status = 'succeeded'",
					Integer.class,
					job_id
			);
			if(count == 1)
			{
				success = true;
				break;
			}
			Thread.sleep(500);
		}


		assertThat(success).isEqualTo(true);

	}
}
