package dev.example.flashsale;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** The card provider. A real network call, about 100 ms, outside our control. */
@Component
public class PaymentGateway {

    private final RestClient http;

    public PaymentGateway(RestClient.Builder builder, @Value("${payments.url}") String url) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(Duration.ofSeconds(2));
        this.http = builder.baseUrl(url).requestFactory(requests).build();
    }

    public String charge(String customer, int amountCents) {
        Map<?, ?> reply = http.post().uri("/charge")
                .body(Map.of("customer", customer, "amountCents", amountCents))
                .retrieve()
                .body(Map.class);
        return String.valueOf(reply.get("ref"));
    }
}
