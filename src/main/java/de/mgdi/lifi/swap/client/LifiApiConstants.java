package de.mgdi.lifi.swap.client;

/**
 * Constants for the LI.FI REST API — base URL, endpoints, headers, and query parameter names.
 *
 * @author mgdi consulting
 */
public final class LifiApiConstants {

    private LifiApiConstants() {
    }

    public static final String QUOTE_URL = "/quote?fromChain=%s&toChain=%s&fromToken=%s&toToken=%s&fromAmount=%s&fromAddress=%s";

    public static final String HEADER_ACCEPT       = "Accept";

    public static final String PARAM_TO_ADDRESS = "toAddress";
    public static final String PARAM_SLIPPAGE   = "slippage";
}
