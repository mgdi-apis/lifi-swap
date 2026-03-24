# lifi-swap

Java library for executing token swaps on EVM chains via the [LI.FI API](https://li.fi).

Handles the full swap lifecycle: fetching a quote, checking and submitting token approvals if needed, signing the transaction locally, and submitting it to the network — all with a single method call.

---

## Requirements

- Java 21+
- Maven
- An EVM wallet (private key as hex string)
- No API key required (LI.FI free tier: up to 200 requests/min)

---

## Build from source

```bash
git clone https://github.com/mgdi/lifi-swap.git
cd lifi-swap
mvn install
```

Then add it as a local dependency:

```xml
<dependency>
    <groupId>de.mgdi</groupId>
    <artifactId>lifi-swap</artifactId>
    <version>1.0.0</version>
</dependency>
```

SLF4J is used for logging (`slf4j-api` is a transitive dependency). Add a logging backend of your choice (e.g. Logback, Log4j2).

---

## Security warning — private key handling

> **This library signs transactions locally using your private key. The key never leaves your machine and is never sent to LI.FI or any external service.**
>
> However, you are fully responsible for how you store and pass the private key:
>
> - **Never hardcode a private key in source code.** Anyone with access to your repository will have access to your funds.
> - **Never commit a `.env` or `application.properties` file containing a real private key.**
> - Load the key at runtime from a secret manager, environment variable, or a vault — never from a file checked into version control.
> - Use a dedicated wallet with only the funds needed for the swap. Do not use a wallet that holds significant assets.
> - If you believe a private key has been exposed, move your funds immediately to a new wallet.
>
> Losing control of your private key means losing all assets associated with that address — this cannot be undone.

---

## How it works

1. **Quote** — calls `GET /quote` with the token pair, amount, wallet address, and optional config; LI.FI returns a ready-to-sign transaction and routing metadata
2. **Approve** (if `ensureApproval=true`) — checks the token's on-chain allowance; if the router is not approved to spend the required amount, sends an `approve(spender, MAX_UINT256)` transaction first and waits for confirmation
3. **Sign** — the swap transaction is signed locally using Web3j and your private key; the chain ID is included to prevent replay attacks
4. **Submit** — the signed transaction is submitted via `eth_sendRawTransaction`
5. **Wait** (optional, `swapAndWait`) — polls for the transaction receipt and verifies on-chain success status

---

## Usage

### Plain Java

#### Fire and forget — returns transaction hash

```java
LifiSwap lifiSwap = LifiSwap.builder()
        .onNetwork(NetworkEnum.BASE)
        .lifiClient(
                LifiHttpClient.builder()
                        .baseUrl("https://li.quest/v1")
                        .build()
        )
        .credentials(Credentials.create(System.getenv("WALLET_PRIVATE_KEY")))
        .build();

String txHash = lifiSwap.swap(USDC, WETH, "100000");
```

#### Wait for on-chain confirmation

```java
String txHash = lifiSwap.swapAndWait(USDC, WETH, "100000");
```

#### With automatic token approval

```java
LifiSwap lifiSwap = LifiSwap.builder()
        .onNetwork(NetworkEnum.BASE)
        .lifiClient(
                LifiHttpClient.builder()
                        .baseUrl("https://li.quest/v1")
                        .build()
        )
        .credentials(Credentials.create(System.getenv("WALLET_PRIVATE_KEY")))
        .ensureApproval(true)   // checks allowance and approves automatically if needed
        .build();

String txHash = lifiSwap.swap(USDC, WETH, "100000");
```

If the LI.FI router is not yet approved to spend your tokens, the library will send an `approve` transaction first and wait for it to confirm before executing the swap.

#### Custom configuration

```java
LifiSwap lifiSwap = LifiSwap.builder()
        .onNetwork(NetworkEnum.ARBITRUM)
        .lifiClient(
                LifiHttpClient.builder()
                        .baseUrl("https://li.quest/v1")
                        .connectTimeout(Duration.ofSeconds(5))
                        .requestTimeout(Duration.ofSeconds(15))
                        .build()
        )
        .credentials(Credentials.create(System.getenv("WALLET_PRIVATE_KEY")))
        .ensureApproval(true)
        .config(
                LifiSwapConfig.builder()
                        .slippage(0.01)
                        .waitTimeout(Duration.ofSeconds(60))
                        .maxRetries(6)
                        .build()
        )
        .build();
```

---

## Configuration

### LifiSwapConfig

All parameters are optional. Use `LifiSwapConfig.defaultConfig()` as a starting point.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `slippage` | `Double` | `0.005` | Maximum accepted slippage as a decimal (e.g. `0.005` = 0.5%). If `null`, LI.FI uses its own default. |
| `waitTimeout` | `Duration` | `30s` | Maximum time to wait for a transaction receipt when using `swapAndWait`. |
| `maxRetries` | `int` | `3` | Number of polling attempts within `waitTimeout`. Poll interval = `waitTimeout / maxRetries`. |

### LifiHttpClient

| Parameter | Type | Default | Description |
|---|---|---|---|
| `baseUrl` | `String` | — | LI.FI API base URL, e.g. `https://li.quest/v1`. |
| `connectTimeout` | `Duration` | `10s` | HTTP connection timeout. |
| `requestTimeout` | `Duration` | `30s` | HTTP request timeout. |

---

## Approval

When `ensureApproval(true)` is set, the library performs the following before every swap:

1. Calls `allowance(walletAddress, spenderAddress)` on the token contract
2. If the current allowance is less than the required amount, sends an `approve(spender, MAX_UINT256)` transaction
3. Polls for the approval receipt and verifies it succeeded on-chain
4. Only then proceeds with the swap

For native tokens (e.g. ETH), the approval check is skipped entirely.

> **Be aware:** Using `MAX_UINT256` grants unlimited spending permission to the LI.FI router for that token. This is standard practice but means the router can spend any amount of that token from your wallet until you revoke the approval. Use a dedicated wallet with only the funds needed for the swap.

---

## Spring Boot

Define the `LifiSwap` bean in a `@Configuration` class and inject it wherever needed.

```java
@Configuration
public class LifiConfig {

    @Value("${lifi.base-url}")
    private String baseUrl;

    @Value("${wallet.private-key}")
    private String privateKey;

    @Bean
    public LifiSwap lifiSwap() {
        return LifiSwap.builder()
                .onNetwork(NetworkEnum.BASE)
                .lifiClient(
                        LifiHttpClient.builder()
                                .baseUrl(baseUrl)
                                .build()
                )
                .credentials(Credentials.create(privateKey))
                .ensureApproval(true)
                .config(LifiSwapConfig.defaultConfig())
                .build();
    }
}
```

`application.properties`:
```properties
lifi.base-url=https://li.quest/v1
wallet.private-key=${WALLET_PRIVATE_KEY}
```

Set the environment variable at runtime — never hardcode it in the properties file.

Inject and use:
```java
@Service
public class TradingService {

    private final LifiSwap lifiSwap;

    public TradingService(LifiSwap lifiSwap) {
        this.lifiSwap = lifiSwap;
    }

    public String executeUsdcToWeth(String amount) throws Exception {
        return lifiSwap.swapAndWait(USDC, WETH, amount);
    }
}
```

Add Logback to get logs from the library:
```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
</dependency>
```

---

## Quarkus

Register the `LifiSwap` instance as an `@ApplicationScoped` CDI bean.

```java
@ApplicationScoped
public class LifiSwapProducer {

    @ConfigProperty(name = "lifi.base-url")
    String baseUrl;

    @ConfigProperty(name = "wallet.private-key")
    String privateKey;

    @Produces
    @ApplicationScoped
    public LifiSwap lifiSwap() {
        return LifiSwap.builder()
                .onNetwork(NetworkEnum.BASE)
                .lifiClient(
                        LifiHttpClient.builder()
                                .baseUrl(baseUrl)
                                .build()
                )
                .credentials(Credentials.create(privateKey))
                .ensureApproval(true)
                .config(LifiSwapConfig.defaultConfig())
                .build();
    }
}
```

`application.properties`:
```properties
lifi.base-url=https://li.quest/v1
wallet.private-key=${WALLET_PRIVATE_KEY}
```

Inject and use:
```java
@ApplicationScoped
public class TradingService {

    @Inject
    LifiSwap lifiSwap;

    public String executeUsdcToWeth(String amount) throws Exception {
        return lifiSwap.swapAndWait(USDC, WETH, amount);
    }
}
```

Quarkus includes JBoss Logging bridged to SLF4J by default — no additional logging dependency needed.

---

## Supported networks

24 EVM networks are available via `NetworkEnum`, each with a built-in public RPC endpoint:

| Network | Chain ID |
|---|---|
| Ethereum | 1 |
| Base | 8453 |
| Arbitrum | 42161 |
| Optimism | 10 |
| Polygon | 137 |
| Avalanche | 43114 |
| BNB Smart Chain | 56 |
| Gnosis | 100 |
| Fantom | 250 |
| Linea | 59144 |
| zkSync Era | 324 |
| Scroll | 534352 |
| Mantle | 5000 |
| Metis | 1088 |
| Celo | 42220 |
| Cronos | 25 |
| Moonbeam | 1284 |
| Moonriver | 1285 |
| Aurora | 1313161554 |
| Boba | 288 |
| Fuse | 122 |
| Velas | 106 |
| OKC | 66 |
| Harmony | 1666600000 |

You can override the RPC endpoint by providing a custom `web3jFactory` on the builder.

---

## Common token addresses (Base)

| Token | Address |
|---|---|
| USDC | `0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913` |
| WETH | `0x4200000000000000000000000000000000000006` |
| ETH (native) | `0x0000000000000000000000000000000000000000` |

---

## License

MIT
