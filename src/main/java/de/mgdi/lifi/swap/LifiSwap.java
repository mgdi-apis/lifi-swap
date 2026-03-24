package de.mgdi.lifi.swap;

import de.mgdi.lifi.swap.client.LifiHttpClient;
import de.mgdi.lifi.swap.config.LifiSwapConfig;
import de.mgdi.lifi.swap.config.NetworkEnum;
import de.mgdi.lifi.swap.model.LifiQuoteRequest;
import de.mgdi.lifi.swap.model.LifiQuoteResponse;
import de.mgdi.lifi.swap.model.LifiTransactionRequest;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;
import org.awaitility.Awaitility;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * Entry point for executing token swaps via the LI.FI API on EVM chains.
 * Fetches a quote (including ready-to-send calldata) from LI.FI, signs the transaction
 * locally with the provided credentials, and submits it on-chain via Web3j.
 *
 * @author mgdi consulting
 */
@Slf4j
@Builder
public class LifiSwap {

    private static final BigInteger MAX_UINT256 = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);
    private static final String NATIVE_TOKEN = "0x0000000000000000000000000000000000000000";

    private final LifiHttpClient lifiClient;
    private final Credentials credentials;
    private final NetworkEnum onNetwork;
    private final Boolean ensureApproval;

    @Builder.Default
    private final LifiSwapConfig config = LifiSwapConfig.defaultConfig();

    @Builder.Default
    private final Function<String, Web3j> web3jFactory = url -> Web3j.build(new HttpService(url));

    /**
     * Submits the swap and returns the transaction hash immediately without waiting for confirmation.
     */
    public String swap(final String fromToken, final String toToken,
                       final String fromAmount) throws Exception {
        final Web3j web3j = web3jFactory.apply(onNetwork.getRpcUrl());
        return executeSwap(fromToken, toToken, fromAmount, web3j);
    }

    /**
     * Submits the swap, waits for the transaction to be mined, and verifies the status.
     * Throws an exception if the transaction reverts on-chain.
     *
     * @return transaction hash of the confirmed swap
     */
    public String swapAndWait(final String fromToken, final String toToken,
                              final String fromAmount) throws Exception {
        final Web3j web3j = web3jFactory.apply(onNetwork.getRpcUrl());
        final String txHash = executeSwap(fromToken, toToken, fromAmount, web3j);
        waitForSuccess(web3j, txHash, "Swap");
        return txHash;
    }

    private String executeSwap(final String fromToken, final String toToken,
                               final String fromAmount, final Web3j web3j) throws Exception {

        final LifiQuoteResponse quote = lifiClient.quote(
                LifiQuoteRequest.builder()
                        .fromChain(onNetwork.getNetworkId())
                        .toChain(onNetwork.getNetworkId())
                        .fromToken(fromToken)
                        .toToken(toToken)
                        .fromAmount(fromAmount)
                        .fromAddress(credentials.getAddress())
                        .slippage(config.getSlippage())
                        .build()
        );

        final LifiTransactionRequest tx = quote.getTransactionRequest();

        if (Boolean.TRUE.equals(ensureApproval) && !NATIVE_TOKEN.equalsIgnoreCase(fromToken)) {
            checkAndApprove(web3j, fromToken, tx.getTo(), new BigInteger(fromAmount), tx.getGasPrice());
        }

        final BigInteger nonce = web3j.ethGetTransactionCount(
                credentials.getAddress(),
                DefaultBlockParameterName.PENDING
        ).send().getTransactionCount();

        final RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce,
                Numeric.decodeQuantity(tx.getGasPrice()),
                Numeric.decodeQuantity(tx.getGasLimit()),
                tx.getTo(),
                Numeric.decodeQuantity(tx.getValue()),
                tx.getData()
        );

        final byte[] signedTx = TransactionEncoder.signMessage(rawTransaction, onNetwork.getNetworkId(), credentials);
        final String hexSignedTx = Numeric.toHexString(signedTx);

        final EthSendTransaction sendResponse = web3j.ethSendRawTransaction(hexSignedTx).send();

        if (sendResponse.hasError()) {
            throw new RuntimeException("Transaction failed: " + sendResponse.getError().getMessage());
        }

        final String txHash = sendResponse.getTransactionHash();
        log.info("Swap submitted txHash={} fromToken={} toToken={} amount={}", txHash, fromToken, toToken, fromAmount);
        return txHash;
    }

    private void checkAndApprove(final Web3j web3j, final String tokenAddress,
                                 final String spender, final BigInteger requiredAmount,
                                 final String gasPrice) throws Exception {

        final org.web3j.abi.datatypes.Function allowanceFn = new org.web3j.abi.datatypes.Function(
                "allowance",
                Arrays.asList(new Address(credentials.getAddress()), new Address(spender)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );

        final EthCall callResult = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        credentials.getAddress(), tokenAddress, FunctionEncoder.encode(allowanceFn)),
                DefaultBlockParameterName.LATEST
        ).send();

        BigInteger allowance = BigInteger.ZERO;
        if (!callResult.hasError() && callResult.getValue() != null && !callResult.getValue().equals("0x")) {
            allowance = (BigInteger) FunctionReturnDecoder
                    .decode(callResult.getValue(), allowanceFn.getOutputParameters())
                    .get(0).getValue();
        }

        log.info("Allowance for {} on spender {}: {}", tokenAddress, spender, allowance);

        if (allowance.compareTo(requiredAmount) >= 0) {
            log.info("Allowance sufficient, skipping approve");
            return;
        }

        log.info("Sending approve tx for token={} spender={}", tokenAddress, spender);

        final org.web3j.abi.datatypes.Function approveFn = new org.web3j.abi.datatypes.Function(
                "approve",
                Arrays.asList(new Address(spender), new Uint256(MAX_UINT256)),
                Collections.emptyList()
        );

        final BigInteger approveNonce = web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.PENDING
        ).send().getTransactionCount();

        final RawTransaction approveTx = RawTransaction.createTransaction(
                approveNonce,
                Numeric.decodeQuantity(gasPrice),
                BigInteger.valueOf(60_000),
                tokenAddress,
                BigInteger.ZERO,
                FunctionEncoder.encode(approveFn)
        );

        final byte[] signedApprove = TransactionEncoder.signMessage(approveTx, onNetwork.getNetworkId(), credentials);
        final EthSendTransaction approveResponse = web3j.ethSendRawTransaction(Numeric.toHexString(signedApprove)).send();

        if (approveResponse.hasError()) {
            throw new RuntimeException("Approve tx failed: " + approveResponse.getError().getMessage());
        }

        final String approveTxHash = approveResponse.getTransactionHash();
        log.info("Approve submitted: {}", approveTxHash);
        waitForSuccess(web3j, approveTxHash, "Approve");
        log.info("Approve confirmed: {}", approveTxHash);
    }

    private void waitForSuccess(final Web3j web3j, final String txHash,
                                final String label) throws Exception {
        final Duration pollInterval = config.getWaitTimeout().dividedBy(config.getMaxRetries());

        Awaitility.await()
                .atMost(config.getWaitTimeout())
                .pollInterval(pollInterval)
                .until(() -> {
                    final Optional<TransactionReceipt> receipt =
                            web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
                    if (receipt.isPresent()) {
                        if (!"0x1".equals(receipt.get().getStatus())) {
                            throw new RuntimeException(label + " tx reverted on-chain: " + txHash);
                        }
                        log.info("{} confirmed: {}", label, txHash);
                        return true;
                    }
                    return false;
                });
    }
}
