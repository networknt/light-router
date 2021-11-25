package com.networknt.router.middleware;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.networknt.exception.ClientException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import com.networknt.client.Http2Client;
import com.networknt.httpstring.HttpStringConstants;

import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

@Ignore
public class BaseServiceHandlerTest {
    static final Logger logger = LoggerFactory.getLogger(PathPrefixServiceHandlerTest.class);

    @Test
    public void testV1Address() throws Exception {
        String url = "http://localhost:7080";
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
        String url = "http://localhost:7080";
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
        String url = "http://localhost:7080";
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
        String url = "http://localhost:7080";
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
        String url = "http://localhost:7080";
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
