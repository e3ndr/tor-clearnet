package xyz.e3ndr.tor_clearnet.blocklists;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import lombok.SneakyThrows;
import okhttp3.Request;
import okhttp3.Response;

public class AhmiaBlocklist extends Blocklist {
    private String list = "";

    @Override
    protected void refresh() throws IOException {
        try (Response response = http.newCall(
            new Request.Builder()
                .url("https://ahmia.fi/blacklist/banned/")
                .build()
        ).execute()) {
            this.list = response.body().string();
        }
    }

    @Override
    protected boolean checkDomain0(String domain) {
        return this.list.contains(md5hash(domain));
    }

    @Override
    protected String name() {
        return "Ahmia.fi";
    }

    @SneakyThrows
    private static String md5hash(String input) {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }

}
