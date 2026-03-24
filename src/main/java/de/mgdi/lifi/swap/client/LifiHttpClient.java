package de.mgdi.lifi.swap.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mgdi.lifi.swap.model.LifiQuoteRequest;
import de.mgdi.lifi.swap.model.LifiQuoteResponse;
import lombok.Builder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP implementation of {@link LifiClient} using the Java built-in {@link HttpClient}.
 * Communicates with the LI.FI REST API to fetch swap quotes including ready-to-send transaction data.
 *
 * @author mgdi consulting
 */
public class LifiHttpClient implements LifiClient {

    private final String baseUrl;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Builder
    private LifiHttpClient(final String baseUrl, final Duration connectTimeout, final Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(30);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout != null ? connectTimeout : Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public LifiQuoteResponse quote(final LifiQuoteRequest request) {
        try {
            final StringBuilder uri = new StringBuilder(String.format(
                    LifiApiConstants.QUOTE_URL,
                    request.getFromChain(),
                    request.getToChain(),
                    request.getFromToken(),
                    request.getToToken(),
                    request.getFromAmount(),
                    request.getFromAddress()
            ));

            appendParam(uri, LifiApiConstants.PARAM_TO_ADDRESS, request.getToAddress());
            appendParam(uri, LifiApiConstants.PARAM_SLIPPAGE, request.getSlippage());

            final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + uri))
                    .timeout(requestTimeout)
                    .header(LifiApiConstants.HEADER_ACCEPT, "application/json")
                    .GET();

            final HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("LI.FI quote API error " + response.statusCode() + ": " + response.body());
            }

            return objectMapper.readValue(response.body(), LifiQuoteResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to get LI.FI quote", e);
        }
    }

    private void appendParam(final StringBuilder uri, final String name, final Object value) {
        if (value != null) {
            uri.append('&').append(name).append('=').append(value);
        }
    }
}
