package de.mgdi.lifi.swap.model;

import lombok.Builder;
import lombok.Data;

/**
 * Request model for the LI.FI GET /quote endpoint.
 * Required: fromChain, toChain, fromToken, toToken, fromAmount, fromAddress.
 * Optional fields are omitted from the query string when null.
 *
 * @author mgdi consulting
 */
@Data
@Builder
public class LifiQuoteRequest {

    /** Source chain ID (e.g. 1 for Ethereum, 8453 for Base). */
    private final long fromChain;

    /** Destination chain ID. Same as fromChain for same-chain swaps. */
    private final long toChain;

    /** Token address or symbol to sell (e.g. "USDC" or "0xa0b86991..."). */
    private final String fromToken;

    /** Token address or symbol to buy. */
    private final String toToken;

    /** Amount to sell in smallest unit (wei for 18-decimal tokens, 6 decimals for USDC). */
    private final String fromAmount;

    /** Wallet address that will sign and send the transaction. */
    private final String fromAddress;

    /** Destination wallet address. Defaults to fromAddress when null. */
    private final String toAddress;

    /**
     * Slippage tolerance as a decimal (e.g. 0.005 = 0.5%).
     * Defaults to LI.FI platform default when null.
     */
    private final Double slippage;
}
