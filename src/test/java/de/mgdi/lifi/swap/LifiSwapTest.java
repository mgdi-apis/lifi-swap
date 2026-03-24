package de.mgdi.lifi.swap;

import de.mgdi.lifi.swap.client.LifiHttpClient;
import de.mgdi.lifi.swap.config.LifiSwapConfig;
import de.mgdi.lifi.swap.config.NetworkEnum;
import de.mgdi.lifi.swap.model.LifiQuoteResponse;
import de.mgdi.lifi.swap.model.LifiTransactionRequest;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LifiSwap}.
 *
 * @author mgdi consulting
 */
class LifiSwapTest {

    private static final String TEST_PRIVATE_KEY =
            "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String USDC           = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913";
    private static final String WETH           = "0x4200000000000000000000000000000000000006";
    private static final String NATIVE_TOKEN   = "0x0000000000000000000000000000000000000000";
    private static final String ROUTER         = "0x1231DEB6f5749EF6cE6943a275A1D3E7486F4EaE";
    private static final String TX_HASH        = "0xabc123";
    private static final String APPROVE_TX_HASH = "0xapprove456";
    private static final String ZERO_ALLOWANCE = "0x" + "0".repeat(64);
    private static final String MAX_ALLOWANCE  = "0x" + "f".repeat(64);

    private LifiHttpClient mockLifiClient;
    private Web3j mockWeb3j;
    private Credentials credentials;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        mockLifiClient = mock(LifiHttpClient.class);
        mockWeb3j      = mock(Web3j.class);
        credentials    = Credentials.create(TEST_PRIVATE_KEY);

        // default: nonce = 0
        Request<Object, EthGetTransactionCount> txCountReq = mock(Request.class);
        EthGetTransactionCount txCount = mock(EthGetTransactionCount.class);
        when(txCount.getTransactionCount()).thenReturn(BigInteger.ZERO);
        when(txCountReq.send()).thenReturn(txCount);
        doReturn(txCountReq).when(mockWeb3j).ethGetTransactionCount(anyString(), any());

        // default: send tx returns TX_HASH
        Request<Object, EthSendTransaction> sendReq = mock(Request.class);
        EthSendTransaction sendTx = mock(EthSendTransaction.class);
        when(sendTx.hasError()).thenReturn(false);
        when(sendTx.getTransactionHash()).thenReturn(TX_HASH);
        when(sendReq.send()).thenReturn(sendTx);
        doReturn(sendReq).when(mockWeb3j).ethSendRawTransaction(anyString());
    }

    private LifiSwap buildSwap(boolean ensureApproval, LifiSwapConfig config) {
        return LifiSwap.builder()
                .onNetwork(NetworkEnum.BASE)
                .lifiClient(mockLifiClient)
                .credentials(credentials)
                .ensureApproval(ensureApproval)
                .config(config)
                .web3jFactory(url -> mockWeb3j)
                .build();
    }

    private LifiQuoteResponse quoteResponse() {
        LifiTransactionRequest tx = new LifiTransactionRequest();
        tx.setFrom(credentials.getAddress());
        tx.setTo(ROUTER);
        tx.setValue("0x0");
        tx.setGasPrice("0x3b9aca00");
        tx.setGasLimit("0x493e0");
        tx.setData("0xcalldata");

        LifiQuoteResponse resp = new LifiQuoteResponse();
        resp.setTransactionRequest(tx);
        return resp;
    }

    private LifiSwapConfig fastConfig() {
        return LifiSwapConfig.builder()
                .slippage(0.005)
                .waitTimeout(Duration.ofSeconds(5))
                .maxRetries(3)
                .build();
    }

    @Test
    void swap_success_returnsTransactionHash() throws Exception {
        when(mockLifiClient.quote(any())).thenReturn(quoteResponse());

        String result = buildSwap(false, LifiSwapConfig.defaultConfig()).swap(USDC, WETH, "100000");

        assertThat(result).isEqualTo(TX_HASH);
    }

    @Test
    @SuppressWarnings("unchecked")
    void swap_sendError_throwsException() throws Exception {
        when(mockLifiClient.quote(any())).thenReturn(quoteResponse());

        Request<Object, EthSendTransaction> sendReq = mock(Request.class);
        EthSendTransaction sendTx = mock(EthSendTransaction.class);
        when(sendTx.hasError()).thenReturn(true);
        Response.Error error = new Response.Error();
        error.setCode(1);
        error.setMessage("nonce too low");
        when(sendTx.getError()).thenReturn(error);
        when(sendReq.send()).thenReturn(sendTx);
        doReturn(sendReq).when(mockWeb3j).ethSendRawTransaction(anyString());

        assertThatThrownBy(() -> buildSwap(false, LifiSwapConfig.defaultConfig()).swap(USDC, WETH, "100000"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("nonce too low");
    }

    @Test
    @SuppressWarnings("unchecked")
    void swapAndWait_confirmedReceipt_returnsHash() throws Exception {
        when(mockLifiClient.quote(any())).thenReturn(quoteResponse());

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        EthGetTransactionReceipt ethReceipt = mock(EthGetTransactionReceipt.class);
        when(ethReceipt.getTransactionReceipt()).thenReturn(Optional.of(receipt));
        Request<Object, EthGetTransactionReceipt> receiptReq = mock(Request.class);
        when(receiptReq.send()).thenReturn(ethReceipt);
        doReturn(receiptReq).when(mockWeb3j).ethGetTransactionReceipt(TX_HASH);

        String result = buildSwap(false, fastConfig()).swapAndWait(USDC, WETH, "100000");

        assertThat(result).isEqualTo(TX_HASH);
    }

    @Test
    @SuppressWarnings("unchecked")
    void swapAndWait_revertedReceipt_throwsException() throws Exception {
        when(mockLifiClient.quote(any())).thenReturn(quoteResponse());

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x0");
        EthGetTransactionReceipt ethReceipt = mock(EthGetTransactionReceipt.class);
        when(ethReceipt.getTransactionReceipt()).thenReturn(Optional.of(receipt));
        Request<Object, EthGetTransactionReceipt> receiptReq = mock(Request.class);
        when(receiptReq.send()).thenReturn(ethReceipt);
        doReturn(receiptReq).when(mockWeb3j).ethGetTransactionReceipt(TX_HASH);

        assertThatThrownBy(() -> buildSwap(false, fastConfig()).swapAndWait(USDC, WETH, "100000"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("reverted on-chain");
    }

    @Test
    @SuppressWarnings("unchecked")
    void swapAndWait_timeout_throwsConditionTimeoutException() throws Exception {
        when(mockLifiClient.quote(any())).thenReturn(quoteResponse());

        EthGetTransactionReceipt ethReceipt = mock(EthGetTransactionReceipt.class);
        when(ethReceipt.getTransactionReceipt()).thenReturn(Optional.empty());
        Request<Object, EthGetTransactionReceipt> receiptReq = mock(Request.class);
        when(receiptReq.send()).thenReturn(ethReceipt);
        doReturn(receiptReq).when(mockWeb3j).ethGetTransactionReceipt(TX_HASH);

        LifiSwapConfig timeoutConfig = LifiSwapConfig.builder()
                .slippage(0.005)
                .waitTimeout(Duration.ofMillis(500))
                .maxRetries(2)
                .build();

        assertThatThrownBy(() -> buildSwap(false, timeoutConfig).swapAndWait(USDC, WETH, "100000"))
                .isInstanceOf(ConditionTimeoutException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void swap_ensureApproval_sufficientAllowance_doesNotSendApprove() throws Exception {
        when(mockLifiClient.quote(any())).thenReturn(quoteResponse());

        EthCall ethCall = mock(EthCall.class);
        when(ethCall.hasError()).thenReturn(false);
        when(ethCall.getValue()).thenReturn(MAX_ALLOWANCE);
        Request<Object, EthCall> callReq = mock(Request.class);
        when(callReq.send()).thenReturn(ethCall);
        doReturn(callReq).when(mockWeb3j).ethCall(any(), any());

        buildSwap(true, LifiSwapConfig.defaultConfig()).swap(USDC, WETH, "100000");

        verify(mockWeb3j, times(1)).ethSendRawTransaction(anyString()); // nur swap, kein approve
    }

    @Test
    @SuppressWarnings("unchecked")
    void swap_ensureApproval_insufficientAllowance_sendsApproveFirst() throws Exception {
        when(mockLifiClient.quote(any())).thenReturn(quoteResponse());

        // allowance = 0
        EthCall ethCall = mock(EthCall.class);
        when(ethCall.hasError()).thenReturn(false);
        when(ethCall.getValue()).thenReturn(ZERO_ALLOWANCE);
        Request<Object, EthCall> callReq = mock(Request.class);
        when(callReq.send()).thenReturn(ethCall);
        doReturn(callReq).when(mockWeb3j).ethCall(any(), any());

        // approve → APPROVE_TX_HASH, swap → TX_HASH
        EthSendTransaction approveSend = mock(EthSendTransaction.class);
        when(approveSend.hasError()).thenReturn(false);
        when(approveSend.getTransactionHash()).thenReturn(APPROVE_TX_HASH);
        EthSendTransaction swapSend = mock(EthSendTransaction.class);
        when(swapSend.hasError()).thenReturn(false);
        when(swapSend.getTransactionHash()).thenReturn(TX_HASH);
        Request<Object, EthSendTransaction> sendReq = mock(Request.class);
        when(sendReq.send()).thenReturn(approveSend, swapSend);
        doReturn(sendReq).when(mockWeb3j).ethSendRawTransaction(anyString());

        // approve receipt: success
        TransactionReceipt approveReceipt = new TransactionReceipt();
        approveReceipt.setStatus("0x1");
        EthGetTransactionReceipt ethApproveReceipt = mock(EthGetTransactionReceipt.class);
        when(ethApproveReceipt.getTransactionReceipt()).thenReturn(Optional.of(approveReceipt));
        Request<Object, EthGetTransactionReceipt> approveReceiptReq = mock(Request.class);
        when(approveReceiptReq.send()).thenReturn(ethApproveReceipt);
        doReturn(approveReceiptReq).when(mockWeb3j).ethGetTransactionReceipt(APPROVE_TX_HASH);

        String result = buildSwap(true, fastConfig()).swap(USDC, WETH, "100000");

        assertThat(result).isEqualTo(TX_HASH);
        verify(mockWeb3j, times(2)).ethSendRawTransaction(anyString()); // approve + swap
    }

    @Test
    void swap_ensureApproval_nativeToken_skipsAllowanceCheck() throws Exception {
        when(mockLifiClient.quote(any())).thenReturn(quoteResponse());

        buildSwap(true, LifiSwapConfig.defaultConfig()).swap(NATIVE_TOKEN, WETH, "1000000000000000000");

        verify(mockWeb3j, never()).ethCall(any(), any());
        verify(mockWeb3j, times(1)).ethSendRawTransaction(anyString());
    }
}
