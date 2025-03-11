package xyz.e3ndr.tor_clearnet;

import java.net.InetSocketAddress;
import java.net.Proxy;

import co.casterlabs.rhs.HttpServer;
import co.casterlabs.rhs.HttpServerBuilder;
import co.casterlabs.rhs.protocol.http.HttpProtocol;

public class TorClearnet {

    public static final String SERVICE_NAME = System.getenv().getOrDefault("SERVICE_NAME", "e3ndr/tor-clearnet");

    public static final Proxy PROXY = new Proxy(
        Proxy.Type.SOCKS,
        new InetSocketAddress(
            System.getenv("SOCKS_PROXY"),
            Integer.parseInt(System.getenv("SOCKS_PORT"))
        )
    );

    public static final String[] DOMAINS;
    static {
        String[] domains = {};
        if (System.getenv().containsKey("DOMAINS")) {
            domains = System.getenv("DOMAINS").split(",");
        }
        DOMAINS = domains;
    }

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
