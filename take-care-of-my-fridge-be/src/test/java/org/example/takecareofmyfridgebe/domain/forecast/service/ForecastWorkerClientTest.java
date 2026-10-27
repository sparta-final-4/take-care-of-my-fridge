package org.example.takecareofmyfridgebe.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ForecastWorkerClientTest {

    @Test
    void sendsDateAndHistoryAsJsonAndReadsPredictions() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        AtomicReference<String> received = new AtomicReference<>();
        server.createContext("/predict", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"modelVersion\":\"weekly-mlp-v1\",\"predictions\":[{\"productId\":1,\"quantity\":9}] }"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ForecastWorkerClient client = new ForecastWorkerClient(
                    "http://127.0.0.1:" + server.getAddress().getPort());
            var result = client.predict(LocalDate.of(2026, 10, 26),
                    List.of(new ForecastWorkerClient.Sample(List.of(1L, 2L), 3)),
                    List.of(new ForecastWorkerClient.ProductHistory(1L, List.of(4L, 5L))));

            assertThat(received.get()).contains("\"weekStart\":\"2026-10-26\"");
            assertThat(received.get()).contains("\"productId\":1");
            assertThat(result.modelVersion()).isEqualTo("weekly-mlp-v1");
            assertThat(result.predictions()).containsExactly(new ForecastWorkerClient.Prediction(1L, 9));
        } finally {
            server.stop(0);
        }
    }
}
