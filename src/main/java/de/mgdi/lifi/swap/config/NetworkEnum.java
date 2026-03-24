package de.mgdi.lifi.swap.config;

/**
 * Enum of all networks supported by LI.FI swap, each with its chain ID and a default public RPC URL.
 *
 * @author mgdi consulting
 */
public enum NetworkEnum {

    ETHEREUM(1, "https://eth.llamarpc.com"),
    BASE(8453, "https://mainnet.base.org"),
    POLYGON(137, "https://polygon-rpc.com"),
    ARBITRUM(42161, "https://arb1.arbitrum.io/rpc"),
    OPTIMISM(10, "https://mainnet.optimism.io"),
    AVALANCHE(43114, "https://api.avax.network/ext/bc/C/rpc"),
    BSC(56, "https://bsc-dataseed.binance.org"),
    GNOSIS(100, "https://rpc.gnosischain.com"),
    FANTOM(250, "https://rpc.ftm.tools"),
    LINEA(59144, "https://rpc.linea.build"),
    ZKSYNC(324, "https://mainnet.era.zksync.io"),
    SCROLL(534352, "https://rpc.scroll.io"),
    MANTLE(5000, "https://rpc.mantle.xyz"),
    METIS(1088, "https://andromeda.metis.io/?owner=1088"),
    AURORA(1313161554, "https://mainnet.aurora.dev"),
    MOONBEAM(1284, "https://rpc.api.moonbeam.network"),
    MOONRIVER(1285, "https://rpc.api.moonriver.moonbeam.network"),
    CELO(42220, "https://forno.celo.org"),
    CRONOS(25, "https://evm.cronos.org"),
    FUSE(122, "https://rpc.fuse.io"),
    BOBA(288, "https://mainnet.boba.network"),
    VELAS(106, "https://evmexplorer.velas.com/rpc"),
    OKC(66, "https://exchainrpc.okex.org"),
    HARMONY(1666600000, "https://api.harmony.one"),
    SOLANA(1151111081099710L, "https://api.mainnet-beta.solana.com");

    private final long networkId;
    private final String rpcUrl;

    NetworkEnum(long networkId, String rpcUrl) {
        this.networkId = networkId;
        this.rpcUrl = rpcUrl;
    }

    public long getNetworkId() {
        return networkId;
    }

    public String getRpcUrl() {
        return rpcUrl;
    }
}
