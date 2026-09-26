package com.lifebranch.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ServerApplicationTests {

	@Autowired
	MockMvc mvc;

	@Test
	void healthReportsDryrunByDefault() throws Exception {
		mvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.service").value("ok"))
				.andExpect(jsonPath("$.device.mode").value("dryrun"))
				.andExpect(jsonPath("$.device.status").value("dryrun"))
				.andExpect(jsonPath("$.device.lastEvent").isEmpty())
				.andExpect(jsonPath("$.device.lastAck").isEmpty());
	}

}
