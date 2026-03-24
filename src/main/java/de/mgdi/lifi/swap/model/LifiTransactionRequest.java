package de.mgdi.lifi.swap.model;

import lombok.Data;

/**
 * EVM transaction data returned inside {@link LifiQuoteResponse}.
 * All fields are ready to submit on-chain via web3j — no further encoding needed.
 *
 * @author mgdi consulting
 */
@Data
public class LifiTransactionRequest {

    /** Sender wallet address. */
    private String from;

    /** Contract address to call (LI.FI router). */
    private String to;

    /** Chain ID (hex string). */
    private String chainId;

    /** ABI-encoded calldata (hex string). */
    private String data;

    /** ETH value to send with the transaction (hex string, "0x0" for token-only swaps). */
    private String value;

    /** Gas price in wei (hex string). */
    private String gasPrice;

    /** Gas limit (hex string). */
    private String gasLimit;
}
