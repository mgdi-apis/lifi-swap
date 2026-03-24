package de.mgdi.lifi.swap.config;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

/**
 * Immutable configuration for {@link de.mgdi.lifi.swap.LifiSwap}.
 * Use {@link #defaultConfig()} for sensible defaults or the builder for custom values.
 *
 * @author mgdi consulting
 */
@Value
@Builder
public class LifiSwapConfig {

    /**
     * Slippage tolerance as a decimal (e.g. 0.005 = 0.5%).
     * Passed to the LI.FI /quote endpoint. If null, LI.FI uses its platform default.
     */
    Double slippage;

    /**
     * Maximum time to wait for a transaction to be confirmed when using {@code swapAndWait}.
     */
    Duration waitTimeout;

    /**
     * How many times to poll for a transaction receipt within {@link #waitTimeout}.
     * The poll interval is derived as {@code waitTimeout / maxRetries}.
     */
    int maxRetries;

    public static LifiSwapConfig defaultConfig() {
        return LifiSwapConfig.builder()
                .slippage(0.005)
                .waitTimeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .build();
    }
}
