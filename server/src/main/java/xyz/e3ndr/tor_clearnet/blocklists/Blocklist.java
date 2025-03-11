package xyz.e3ndr.tor_clearnet.blocklists;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import lombok.SneakyThrows;
import okhttp3.OkHttpClient;

public abstract class Blocklist {
    private static final long REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(15);

    private static final List<Blocklist> BLOCKLISTS = new ArrayList<>();
    static {
        List<String> blockLists = Arrays.asList(System.getenv().getOrDefault("BLOCKLISTS", "").toLowerCase().split(","));
        if (blockLists.contains("ahmia")) {
            BLOCKLISTS.add(new AhmiaBlocklist());
        }
    }

    protected static final OkHttpClient http = new OkHttpClient.Builder()
        .build();

    private final ReentrantLock lock = new ReentrantLock();

    private volatile long lastRefresh = 0L;

    @SneakyThrows
    public final boolean checkDomain(String domain) {
        this.lock.tryLock();
        try {
            if (System.currentTimeMillis() > this.lastRefresh + REFRESH_INTERVAL) {
                this.refresh();
                this.lastRefresh = System.currentTimeMillis();
            }
        } finally {
            this.lock.unlock();
        }
        return this.checkDomain0(domain);
    }

    protected abstract void refresh() throws IOException;

    protected abstract String name();

    /**
     * @return true, if the domain should be blocked.
     */
    protected abstract boolean checkDomain0(String domain);

    public static List<String> shouldBlock(String domain) {
        List<String> thoseWhoBlock = new ArrayList<>(BLOCKLISTS.size());
        for (Blocklist blocklist : BLOCKLISTS) {
            if (blocklist.checkDomain(domain)) {
                thoseWhoBlock.add(blocklist.name());
            }
        }
        return thoseWhoBlock;
    }
}
