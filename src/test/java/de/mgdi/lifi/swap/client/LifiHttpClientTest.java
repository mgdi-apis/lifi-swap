package de.mgdi.lifi.swap.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import de.mgdi.lifi.swap.model.LifiQuoteRequest;
import de.mgdi.lifi.swap.model.LifiQuoteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LifiHttpClient}.
 *
 * @author mgdi consulting
 */
class LifiHttpClientTest {

    private static final String QUOTE_RESPONSE_JSON = """
            {
              "id": "test-id",
              "type": "lifi",
              "tool": "uniswap",
              "transactionRequest": {
                "from": "0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf",
                "to": "0x1231DEB6f5749EF6cE6943a275A1D3E7486F4EaE",
                "chainId": "0x2105",
                "data": "0xcalldata",
                "value": "0x0",
                "gasPrice": "0x3b9aca00",
                "gasLimit": "0x493e0"
              },
              "estimate": {
                "fromAmount": "100000",
                "toAmount": "50000000000000",
                "toAmountMin": "49750000000000"
              }
            }
            """;

    private WireMockServer wireMock;
    private LifiHttpClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0); // random port
        wireMock.start();
        client = LifiHttpClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private LifiQuoteRequest baseRequest() {
        return LifiQuoteRequest.builder()
                .fromChain(8453)
                .toChain(8453)
                .fromToken("0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913")
                .toToken("0x4200000000000000000000000000000000000006")
                .fromAmount("100000")
                .fromAddress("0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf")
                .build();
    }

    private void stubQuoteOk() {
        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(QUOTE_RESPONSE_JSON)));
    }

    @Test
    void testQuoteSuccessReturnsResponse() {
        stubQuoteOk();

        LifiQuoteResponse response = client.quote(baseRequest());

        assertThat(response.getId()).isEqualTo("test-id");
        assertThat(response.getTransactionRequest()).isNotNull();
        assertThat(response.getTransactionRequest().getTo())
                .isEqualTo("0x1231DEB6f5749EF6cE6943a275A1D3E7486F4EaE");
        assertThat(response.getEstimate().getFromAmount()).isEqualTo("100000");
    }

    @Test
    void testQuoteServerErrorThrowsException() {
        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        assertThatThrownBy(() -> client.quote(baseRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get");
    }

    @Test
    void testQuoteNotFoundThrowsException() {
        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"message\":\"route not found\"}")));

        assertThatThrownBy(() -> client.quote(baseRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get");
    }

    @Test
    void testQuoteWithoutApiKeyDoesNotSendXApiKeyHeader() {
        stubQuoteOk();

        client.quote(baseRequest());

        wireMock.verify(getRequestedFor(urlPathEqualTo("/quote"))
                .withoutHeader("x-api-key"));
    }

    @Test
    void testQuoteWithSlippageAppendsQueryParam() {
        stubQuoteOk();
        LifiQuoteRequest request = LifiQuoteRequest.builder()
                .fromChain(8453).toChain(8453)
                .fromToken("0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913")
                .toToken("0x4200000000000000000000000000000000000006")
                .fromAmount("100000")
                .fromAddress("0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf")
                .slippage(0.01)
                .build();

        client.quote(request);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/quote"))
                .withQueryParam("slippage", equalTo("0.01")));
    }

    @Test
    void testQuoteWithToAddressAppendsQueryParam() {
        stubQuoteOk();
        LifiQuoteRequest request = LifiQuoteRequest.builder()
                .fromChain(8453).toChain(8453)
                .fromToken("0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913")
                .toToken("0x4200000000000000000000000000000000000006")
                .fromAmount("100000")
                .fromAddress("0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf")
                .toAddress("0xRecipient")
                .build();

        client.quote(request);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/quote"))
                .withQueryParam("toAddress", equalTo("0xRecipient")));
    }

    @Test
    void testQuoteWithoutOptionalParamsDoesNotAppendSlippageOrToAddress() {
        stubQuoteOk();

        client.quote(baseRequest());

        wireMock.verify(getRequestedFor(urlPathEqualTo("/quote"))
                .withoutQueryParam("slippage")
                .withoutQueryParam("toAddress"));
    }
}
