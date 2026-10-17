package com.thinkxecai.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The running server is reachable only from this machine and only under a local host name (ADR 033). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LocalAccessTest {

    @Autowired
    private ServletWebServerApplicationContext context;

    @LocalServerPort
    private int port;

    @Test
    void theServerListensOnTheLoopbackAddressOnly() {
        TomcatWebServer server = (TomcatWebServer) context.getWebServer();
        Object address = server.getTomcat().getConnector().getProperty("address");

        assertThat(address).isInstanceOf(InetAddress.class);
        assertThat(((InetAddress) address).getHostAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void aRebindingRequestIsRefusedAndALocalOneServed() throws IOException {
        // Java's HTTP clients do not let a caller choose the Host header, so the request is written by hand.
        assertThat(statusLine("evil.example:" + port)).isEqualTo("HTTP/1.1 403 ");
        assertThat(statusLine("localhost:" + port)).isEqualTo("HTTP/1.1 200 ");
    }

    private String statusLine(String host) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /health HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            String response = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            return response.substring(0, response.indexOf("\r\n"));
        }
    }

}
