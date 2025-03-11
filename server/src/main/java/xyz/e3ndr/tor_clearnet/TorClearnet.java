package xyz.e3ndr.tor_clearnet;

import java.net.InetSocketAddress;
import java.net.Proxy;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rhs.HttpServer;
import co.casterlabs.rhs.HttpServerBuilder;
import co.casterlabs.rhs.protocol.http.HttpProtocol;

public class TorClearnet {
    public static final String SERVICE_NAME = System.getenv().getOrDefault("SERVICE_NAME", "e3ndr/tor-clearnet");
    public static final @Nullable String SERVICE_DOMAIN = System.getenv("SERVICE_DOMAIN");
    public static final boolean SERVICE_SUPPORTS_HTTPS = "true".equalsIgnoreCase(System.getenv("SERVICE_SUPPORTS_HTTPS"));

    public static final Proxy PROXY = new Proxy(
        Proxy.Type.SOCKS,
        new InetSocketAddress(
            System.getenv("SOCKS_PROXY"),
            Integer.parseInt(System.getenv("SOCKS_PORT"))
        )
    );

    public static void main(String[] args) throws Exception {
        HttpServer server = new HttpServerBuilder()
            .withPort(80)
            .withServerHeader(SERVICE_NAME)
            .withTaskExecutor(RakuraiTaskExecutor.INSTANCE)
            .with(new HttpProtocol(), Handler.INSTANCE)
//            .with(new WebsocketProtocol(), Handler.INSTANCE)
            .build();

        server.start();
    }

}
