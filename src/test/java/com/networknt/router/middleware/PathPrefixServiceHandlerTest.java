package com.networknt.router.middleware;

import com.networknt.client.Http2Client;
import com.networknt.exception.ClientException;
import com.networknt.header.HeaderHandler;
import com.networknt.httpstring.HttpStringConstants;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class PathPrefixServiceHandlerTest {
    static final Logger logger = LoggerFactory.getLogger(PathPrefixServiceHandlerTest.class);

    static Undertow server = null;
    static PathPrefixServiceHandler pathServiceHandler = null;

    @BeforeClass
    public static void setUp() {
        if(server == null) {
            logger.info("starting server");
            HttpHandler handler = getTestHandler();
            pathServiceHandler = new PathPrefixServiceHandler();
            pathServiceHandler.setNext(handler);
            handler = pathServiceHandler;
            HeaderHandler headerHandler = new HeaderHandler();
            headerHandler.setNext(handler);
            handler = headerHandler;
            server = Undertow.builder()
                    .addHttpListener(8080, "localhost")
                    .setHandler(handler)
                    .build();
            server.start();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if(server != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server.stop();
            logger.info("The server is stopped.");
        }
    }

    static RoutingHandler getTestHandler() {
        return Handlers.routing()
                .add(Methods.GET, "/v1/address/{id}", exchange -> {
                    exchange.getResponseHeaders().put(HttpStringConstants.SERVICE_ID, exchange.getRequestHeaders().getFirst(HttpStringConstants.SERVICE_ID));
                    exchange.getResponseHeaders().put(HttpStringConstants.ENV_TAG, exchange.getRequestHeaders().getFirst(HttpStringConstants.ENV_TAG));
                    exchange.getResponseSender().send("OK");
                })
                .add(Methods.GET, "/v2/address", exchange -> {
                    exchange.getResponseHeaders().put(HttpStringConstants.SERVICE_ID, exchange.getRequestHeaders().getFirst(HttpStringConstants.SERVICE_ID));
                    exchange.getResponseHeaders().put(HttpStringConstants.ENV_TAG, exchange.getRequestHeaders().getFirst(HttpStringConstants.ENV_TAG));
                    exchange.getResponseSender().send("OK");
                })
                .add(Methods.POST, "/v1/contact", exchange -> {
                    exchange.getResponseHeaders().put(HttpStringConstants.SERVICE_ID, exchange.getRequestHeaders().getFirst(HttpStringConstants.SERVICE_ID));
                    exchange.getResponseHeaders().put(HttpStringConstants.ENV_TAG, exchange.getRequestHeaders().getFirst(HttpStringConstants.ENV_TAG));
                    exchange.getResponseSender().send("OK");
                });
    }


    @Test
    public void testFindServiceId() throws Exception {
        // Make test parametric when porting to junit5?
        Map<String, String> expected = new HashMap<>();

        // Simple calls
        expected.put("/v1/address/111", "party.address-1.0.0");
        expected.put("/v1/address/whatever", "party.address-1.0.0");
        expected.put("/v2/address", "party.address-2.0.0");
        expected.put("/v1/contact", "party.contact-1.0.0");

        // Missing leading slash
        expected.put("v2/address/irrelevant", "party.address-2.0.0");
        expected.put("v1/contact/johnathan.strange", "party.contact-1.0.0");

        // Unmatched paths
        expected.put("/v1/very/bad/path", null);
        expected.put("/v1/contact-not-really/reject.me", null);

        Map<String, String> result = new HashMap<>();
        for (String path : expected.keySet()) {
            result.put(path, pathServiceHandler.findServiceId(path));
        }

        Assert.assertEquals(expected, result);
    }

    @Test
    public void testV1Address() throws Exception {
        String url = "http://localhost:8080";
        Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/address/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        HeaderMap headerMap = reference.get().getResponseHeaders();
        String serviceId = headerMap.getFirst(HttpStringConstants.SERVICE_ID);
        String envTag = headerMap.getFirst(HttpStringConstants.ENV_TAG);
        Assert.assertEquals(200, statusCode);
        Assert.assertEquals("OK", body);
        Assert.assertEquals("party.address-1.0.0", serviceId);
        Assert.assertEquals("dev", envTag);
    }

    @Test
    public void testV2Address() throws Exception {
        String url = "http://localhost:8080";
        Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v2/address").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        HeaderMap headerMap = reference.get().getResponseHeaders();
        String serviceId = headerMap.getFirst(HttpStringConstants.SERVICE_ID);
        String envTag = headerMap.getFirst(HttpStringConstants.ENV_TAG);
        Assert.assertEquals(200, statusCode);
        Assert.assertEquals("OK", body);
        Assert.assertEquals("party.address-2.0.0", serviceId);
        Assert.assertEquals("dev", envTag);
    }

    @Test
    public void testV1Contact() throws Exception {
        String url = "http://localhost:8080";
        Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/contact").setMethod(Methods.POST);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.CONTENT_TYPE, "application/json");
            request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
            connection.sendRequest(request, client.createClientCallback(reference, latch, "{\"key\":\"value\"}"));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        HeaderMap headerMap = reference.get().getResponseHeaders();
        String serviceId = headerMap.getFirst(HttpStringConstants.SERVICE_ID);
        String envTag = headerMap.getFirst(HttpStringConstants.ENV_TAG);
        Assert.assertEquals(200, statusCode);
        Assert.assertEquals("OK", body);
        Assert.assertEquals("party.contact-1.0.0", serviceId);
        Assert.assertEquals("dev", envTag);
    }


    @Test
    public void testBadPath() throws Exception {
        String url = "http://localhost:8080";
        Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/bad/path").setMethod(Methods.POST);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.CONTENT_TYPE, "application/json");
            request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
            connection.sendRequest(request, client.createClientCallback(reference, latch, "{\"key\":\"value\"}"));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(404, statusCode);
    }


    @Test
    public void testServiceIdHeaderOverride() throws Exception {
        String url = "http://localhost:8080";
        Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/address/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "party.address-2.0.0");
            request.getRequestHeaders().put(Headers.CONTENT_TYPE, "application/json");
            request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
            connection.sendRequest(request, client.createClientCallback(reference, latch, "{\"key\":\"value\"}"));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        HeaderMap headerMap = reference.get().getResponseHeaders();
        String serviceId = headerMap.getFirst(HttpStringConstants.SERVICE_ID);
        String envTag = headerMap.getFirst(HttpStringConstants.ENV_TAG);
        Assert.assertEquals(200, statusCode);
        Assert.assertEquals("OK", body);
        Assert.assertEquals("party.address-2.0.0", serviceId);
        Assert.assertEquals("dev", envTag);
    }

}
