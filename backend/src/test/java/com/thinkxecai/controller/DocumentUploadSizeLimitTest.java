package com.thinkxecai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 1 MB per-file limit is enforced by the servlet container while it reads the multipart
 * request, which MockMvc skips, so this test talks to a real server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentUploadSizeLimitTest {

    private static final int ONE_MEGABYTE = 1024 * 1024;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void aFileOfExactlyOneMegabytePassesTheSizeLimit() {
        // Reaches the service, which then refuses the unknown knowledge base.
        JsonNode response = upload(ONE_MEGABYTE);

        assertThat(response.get("code").asInt()).isEqualTo(400);
        assertThat(response.get("message").asText()).startsWith("kbId: unknown knowledge base");
    }

    @Test
    void aLargerFileIsRefusedWithTheLimitInTheMessage() {
        JsonNode response = upload(ONE_MEGABYTE + 1);

        assertThat(response.get("code").asInt()).isEqualTo(400);
        assertThat(response.get("message").asText()).isEqualTo("file: too large (maximum 1 MB)");
    }

    @Test
    void aMalformedMultipartBodyIsAClientError() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=b"));
        // a part that starts but never ends: the container cannot read the body
        String truncated = "--b\r\nContent-Disposition: form-data; name=\"kbId\"\r\n\r\nabc";

        JsonNode response = restTemplate.postForObject("/api/documents/upload",
                new HttpEntity<>(truncated, headers), JsonNode.class);

        assertThat(response.get("code").asInt()).isEqualTo(400);
        assertThat(response.get("message").asText()).isEqualTo("Malformed multipart request");
    }

    private JsonNode upload(int size) {
        byte[] content = new byte[size];
        content[0] = '#';
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("kbId", UUID.randomUUID().toString());
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "big.md";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.postForObject("/api/documents/upload", new HttpEntity<>(form, headers), JsonNode.class);
    }

}
