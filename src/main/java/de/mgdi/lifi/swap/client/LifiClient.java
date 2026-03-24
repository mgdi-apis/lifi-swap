package de.mgdi.lifi.swap.client;

import de.mgdi.lifi.swap.model.LifiQuoteRequest;
import de.mgdi.lifi.swap.model.LifiQuoteResponse;

/**
 * Abstraction over the LI.FI REST API.
 *
 * @author mgdi consulting
 */
public interface LifiClient {

    LifiQuoteResponse quote(LifiQuoteRequest request);
}
