package xyz.e3ndr.tor_clearnet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.rhs.HttpStatus;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.HeaderValue;
import co.casterlabs.rhs.protocol.exceptions.DropConnectionException;
import co.casterlabs.rhs.protocol.exceptions.HttpException;
import co.casterlabs.rhs.protocol.http.HttpProtocol.HttpProtoHandler;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpResponse.ResponseContent;
import co.casterlabs.rhs.protocol.http.HttpSession;
import kotlin.Pair;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http.HttpMethod;
import okio.BufferedSink;
import okio.Okio;
import xyz.e3ndr.tor_clearnet.blocklists.Blocklist;

public class Handler implements HttpProtoHandler/*, WebsocketHandler*/ {
    private static final List<String> DISALLOWED_HEADERS = Arrays.asList(
        "accept-encoding",
        "connection",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "content-length",
        "front-end-https",
        "te",
        "trailers",
        "host",
        "transfer-encoding",
        "content-encoding",
        "upgrade",
        "sec-websocket-key",
        "sec-websocket-extensions",
        "sec-websocket-version",
        "sec-websocket-protocol",
        "remote-addr",
        "http-client-ip",
        "host",
        "x-forwarded-for",
        "x-forwarded-proto",
        "x-forwarded-ssl",
        "x-remote-ip",
        "x-url-scheme"
    );

    public static final Handler INSTANCE = new Handler();

    private static final OkHttpClient http = new OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .proxy(TorClearnet.PROXY)
        .build();

    @Override
    public HttpResponse handle(HttpSession session) throws HttpException, DropConnectionException {
        String onionHost = HeaderUtils.getOnionAddress(session.uri().host);
        if (onionHost == null) {
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.NOT_FOUND, TorClearnet.SERVICE_NAME + ": Unrecognized clearnet domain: " + session.uri().host);
        }

        boolean onionHttps = onionHost.startsWith("https-");
        if (onionHttps) {
            onionHost = onionHost.substring("https-".length());
        }

        List<String> blockedBy = Blocklist.shouldBlock(onionHost);
        if (!blockedBy.isEmpty()) {
            return HttpResponse.newFixedLengthResponse(
                StandardHttpStatus.UNAVAILABLE_FOR_LEAGAL_REASONS,
                TorClearnet.SERVICE_NAME + ": Domain appears in the following blocklists:\n- " + String.join("\n- ", blockedBy)
            );
        }

        String onionUrl = String.format(
            "%s://%s%s",
            onionHttps ? "https" : "http",
            onionHost, session.uri().rawPath
        );

        Request.Builder builder = new Request.Builder()
            .url(onionUrl)
            .addHeader("X-Clearnet-Proxy", session.uri().host);

        RequestBody body = null;
        if (session.body().present()) {
            body = new RequestBody() {
                @Override
                public MediaType contentType() {
                    return null; // Already handled.
                }

                @Override
                public long contentLength() throws IOException {
                    return session.body().length();
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    sink.writeAll(Okio.source(session.body().stream()));
                }
            };
        }

        if (HttpMethod.requiresRequestBody(session.rawMethod()) && body == null) {
            body = RequestBody.create(new byte[0], null);
        } else if (!HttpMethod.permitsRequestBody(session.rawMethod())) {
            body = null;
        }

        builder.method(session.rawMethod(), body);

        for (Map.Entry<String, List<HeaderValue>> header : session.headers().entrySet()) {
            String key = header.getKey().toLowerCase();
            if (DISALLOWED_HEADERS.contains(key)) continue;

            for (HeaderValue value : header.getValue()) {
                String modifiedValue = value.raw() // Modify to the onion domain.
                    .replace(session.uri().host, onionHost);
                builder.addHeader(key, modifiedValue);
            }
        }

        Request request = builder.build();
        Response response = null;

        try {
            response = http.newCall(request).execute();

            HttpStatus status = HttpStatus.adapt(response.code(), response.message());

            HttpResponse result;
            if (HeaderUtils.isTextType(response.header("Content-Type"))) {
                result = respondText(response, status);
            } else {
                result = respondBinary(response, status);
            }

            for (Pair<? extends String, ? extends String> header : response.headers()) {
                String key = header.getFirst();
                String value = header.getSecond();

                if (key.equalsIgnoreCase("Content-Security-Policy")) {
                    String modifiedValue = Rewriter.rewriteCsp(value);
                    result.header(key, modifiedValue);
                    continue;
                }

                if (key.equalsIgnoreCase("Transfer-Encoding") ||
                    key.equalsIgnoreCase("Content-Length") ||
                    key.equalsIgnoreCase("Content-Encoding") ||
                    key.equalsIgnoreCase("Content-Type")) {
                    continue;
                }

                String modifiedValue = Rewriter.rewrite(value);
                result.header(key, modifiedValue);
            }

            return result
                .mime(response.header("Content-Type"))
                .header("X-Clearnet-Proxy", (onionHttps ? "https://" : "http://") + onionHost);
        } catch (Throwable t) {
            t.printStackTrace();
            if (response != null) {
                response.close();
            }
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.INTERNAL_ERROR, TorClearnet.SERVICE_NAME + ": An internal server error occurred: " + t.getMessage());
        }
    }

    private static HttpResponse respondText(Response response, HttpStatus status) throws IOException {
        String str = Rewriter.rewrite(response.body().string());
        return HttpResponse.newFixedLengthResponse(status, str);
    }

    private static HttpResponse respondBinary(Response response, HttpStatus status) {
        long responseLen = Long.parseLong(response.header("Content-Length", "-1"));
        InputStream responseStream = response.body().byteStream();

        return new HttpResponse(
            new ResponseContent() {
                @Override
                public void write(int recommendedBufferSize, OutputStream out) throws IOException {
                    StreamUtil.streamTransfer(
                        responseStream,
                        out,
                        recommendedBufferSize,
                        responseLen
                    );
                }

                @Override
                public long length() {
                    return responseLen;
                }

                @Override
                public void close() throws IOException {
                    response.close();
                }
            },
            status
        );
    }

//    @Override
//    public WebsocketResponse handle(WebsocketSession session) {
//        // TODO Auto-generated method stub
//        return null;
//    }

}
