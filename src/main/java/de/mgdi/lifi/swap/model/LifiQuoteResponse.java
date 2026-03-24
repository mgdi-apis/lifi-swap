package de.mgdi.lifi.swap.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Response model returned by the LI.FI GET /quote endpoint.
 * Contains the best route found and a ready-to-send EVM transaction in {@link #transactionRequest}.
 *
 * @author mgdi consulting
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LifiQuoteResponse {

    private String id;
    private String type;
    private String tool;
    private LifiTransactionRequest transactionRequest;
    private LifiEstimate estimate;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LifiEstimate {

        /** Amount sent (in smallest unit). */
        private String fromAmount;

        /** Expected amount received (in smallest unit). */
        private String toAmount;

        /** Minimum amount received after slippage (in smallest unit). */
        private String toAmountMin;
    }
}
