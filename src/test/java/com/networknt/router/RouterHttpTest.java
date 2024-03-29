/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.router;

import com.networknt.client.Http2Client;
import com.networknt.common.DecryptUtil;
import com.networknt.common.SecretConstants;
import com.networknt.config.Config;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.server.ServerConfig;
import com.networknt.exception.ClientException;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import javax.net.ssl.*;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static com.networknt.server.Server.TRUST_ALL_CERTS;
import static io.undertow.Handlers.path;

@ExtendWith(TestServer.class)
public class RouterHttpTest {
    static final Logger logger = LoggerFactory.getLogger(RouterHttpTest.class);
    public static final String CONFIG_SECRET = "secret";

    static Undertow server1 = null;
    static Undertow server2 = null;
    static Undertow server3 = null;
    public static TestServer server = TestServer.getInstance();
    public static ServerConfig config = ServerConfig.getInstance();
    public static Map<String, Object> secret = DecryptUtil.decryptMap(Config.getInstance().getJsonMapConfig(CONFIG_SECRET));
    static SSLContext sslContext = createSSLContext();

    static final boolean enableHttp2 = server.getServerConfig().isEnableHttp2();
    static final boolean enableHttps = server.getServerConfig().isEnableHttps();
    static final int httpPort = server.getServerConfig().getHttpPort();
    static final int httpsPort = server.getServerConfig().getHttpsPort();
    static final String url = enableHttp2 || enableHttps ? "https://localhost:" + httpsPort : "http://localhost:" + httpPort;

    @BeforeAll
    public static void setUp() {
        if(server1 == null) {
            logger.info("starting server1");
            Undertow.Builder builder1 = Undertow.builder()
                    .addHttpsListener(18081, "localhost", sslContext)
                    .setHandler(path().addPrefixPath("/v2/address", new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            Map<String, Deque<String>> params = exchange.getQueryParameters();
                            System.out.println("Query Parameters:");
                            for(Map.Entry<String, Deque<String>> param : params.entrySet()) {
                                System.out.println(param.getKey() + " -> " + param.getValue().getFirst());
                            }
                            HeaderMap headerMap = exchange.getRequestHeaders();
                            System.out.println("Headers:");
                            Iterator<HeaderValues> it = headerMap.iterator();
                            while(it.hasNext()) {
                                HeaderValues values = it.next();
                                System.out.println(values.getHeaderName() + " -> " + values.getFirst());
                            }
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server1");
                        }
                    }));
            if(enableHttp2) {
                builder1.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
            }
            server1 = builder1.build();
            server1.start();
        }

        if(server2 == null) {
            logger.info("starting server2");
            Undertow.Builder builder2 = Undertow.builder()
                    .addHttpsListener(18082, "localhost", sslContext)
                    .setHandler(path().addPrefixPath("/v2/address", new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            Map<String, Deque<String>> params = exchange.getQueryParameters();
                            System.out.println("Query Parameters:");
                            for(Map.Entry<String, Deque<String>> param : params.entrySet()) {
                                System.out.println(param.getKey() + " -> " + param.getValue().getFirst());
                            }
                            HeaderMap headerMap = exchange.getRequestHeaders();
                            System.out.println("Headers:");
                            Iterator<HeaderValues> it = headerMap.iterator();
                            while(it.hasNext()) {
                                HeaderValues values = it.next();
                                System.out.println(values.getHeaderName() + " -> " + values.getFirst());
                            }
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server2");
                        }
                    }));

            if(enableHttp2) {
                builder2.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
            }
            server2 = builder2.build();
            server2.start();
        }

        if(server3 == null) {
            logger.info("starting server3");
            Undertow.Builder builder3 = Undertow.builder()
                    .addHttpsListener(18083, "localhost", sslContext)
                    .setHandler(path().addPrefixPath("/v2/address", new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            Map<String, Deque<String>> params = exchange.getQueryParameters();
                            System.out.println("Query Parameters:");
                            for(Map.Entry<String, Deque<String>> param : params.entrySet()) {
                                System.out.println(param.getKey() + " -> " + param.getValue().getFirst());
                            }
                            HeaderMap headerMap = exchange.getRequestHeaders();
                            System.out.println("Headers:");
                            Iterator<HeaderValues> it = headerMap.iterator();
                            while(it.hasNext()) {
                                HeaderValues values = it.next();
                                System.out.println(values.getHeaderName() + " -> " + values.getFirst());
                            }
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server3");
                        }
                    }));

            if(enableHttp2) {
                builder3.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
            }
            server3 = builder3.build();
            server3.start();
        }
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if(server1 != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server1.stop();
            logger.info("The server1 is stopped.");
        }
        if(server2 != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server2.stop();
            logger.info("The server2 is stopped.");
        }
        if(server3 != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server3.stop();
            logger.info("The server3 is stopped.");
        }
    }

    /**
     * Calling server1 directly to ensure that the endpoint is working.
     * @throws Exception
     */
    @Test
    public void testServer1() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("https://localhost:18081"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
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
        Assertions.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assertions.assertEquals("Server1", body);
        }
    }

    /**
     * Calling server1 /v2/address with the /v3/address request to ensure that url rewrite is working
     * @throws Exception
     */
    @Test
    public void testUrlRewrite() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v3/address").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "com.networknt.test-1.0.0");
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
        Assertions.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assertions.assertTrue(body.contains("Server"));
        }
    }

    /**
     * Calling server1 /v2/address with the /v3/address request to ensure that url rewrite is working. Also, the request will
     * have some query parameters and the test will ensure they are all passed to the request handler.
     *
     * @throws Exception
     */
    @Test
    public void testUrlRewriteWithQueryParameters() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v3/address?host=lightapi.net&module=http-sidecar").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "com.networknt.test-1.0.0");
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
        Assertions.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assertions.assertTrue(body.contains("Server"));
        }
    }

    /**
     * Calling server1 /v2/address with the DELETE method will rewrite to GET method
     * @throws Exception
     */
    @Test
    public void testMethodRewrite() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v2/address").setMethod(Methods.DELETE);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "com.networknt.test-1.0.0");
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
        Assertions.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assertions.assertTrue(body.contains("Server"));
        }
    }

    @Test
    public void testGetServiceIdHeaders() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(10);
        final ClientConnection connection;
        logger.debug("url = " + url);
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, enableHttp2 ? OptionMap.create(UndertowOptions.ENABLE_HTTP2, true): OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final List<AtomicReference<ClientResponse>> references = new CopyOnWriteArrayList<>();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        AtomicReference<ClientResponse> reference = new AtomicReference<>();
                        references.add(i, reference);
                        final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath("/v2/address");
                        request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "com.networknt.test-1.0.0");
                        connection.sendRequest(request, client.createClientCallback(reference, latch));
                    }
                }

            });

            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        for (final AtomicReference<ClientResponse> reference : references) {
            String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
            if(logger.isDebugEnabled()) logger.debug("body = " + body);
            Assertions.assertTrue(body.contains("Server"));
        }
    }

    @Test
    public void testGetServiceIdQueryParams() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(10);
        final ClientConnection connection;
        logger.debug("url = " + url);
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, enableHttp2 ? OptionMap.create(UndertowOptions.ENABLE_HTTP2, true): OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final List<AtomicReference<ClientResponse>> references = new CopyOnWriteArrayList<>();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        AtomicReference<ClientResponse> reference = new AtomicReference<>();
                        references.add(i, reference);
                        final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath("/v2/address?service_id=com.networknt.test-1.0.0");
                        // instead of put the serviceId in the header, we are going to use the serviceId in the query parameters.
                        // request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "com.networknt.test-1.0.0");
                        connection.sendRequest(request, client.createClientCallback(reference, latch));
                    }
                }

            });

            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        for (final AtomicReference<ClientResponse> reference : references) {
            String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
            if(logger.isDebugEnabled()) logger.debug("body = " + body);
            Assertions.assertTrue(body.contains("Server"));
        }
    }

    /**
     * Calling server1 /v1/address with the query parameter business-request/value2, module and app-id. After the query parameter
     * rewrite, we are expecting the new key is request-query and value is value2. module is changed to mod and app-id value is
     * changed from esb to emb. This is the test case that we are changing both key and value, change key only and change value only.
     *
     * @throws Exception
     */
    @Test
    public void testQueryParamsRewrite() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v2/address?business-query=value1&module=http-sidecar&app-id=esb").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "com.networknt.test-1.0.0");
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
        Assertions.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assertions.assertTrue(body.contains("Server"));
        }
    }

    /**
     * Calling server1 /v1/address with the headers business-request/value2, module and app-id. After the header
     * rewrite, we are expecting the new key is request-query and value is value2. module is changed to mod. app-id
     * value is changed from esb to emb. This is the test case that we are changing both key and value, change key
     * only and change value only.
     *
     * @throws Exception
     */
    @Test
    public void testHeaderRewrite() throws Exception {
        final Http2Client client = Http2Client.getInstance();
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
            request.getRequestHeaders().put(new HttpString("business-query"), "value1");
            request.getRequestHeaders().put(new HttpString("module"), "http-sidecar");
            request.getRequestHeaders().put(new HttpString("app-id"), "esb");
            request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "com.networknt.test-1.0.0");
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
        Assertions.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assertions.assertTrue(body.contains("Server"));
        }
    }


    private static SSLContext createSSLContext() throws RuntimeException {
        try {
            KeyManager[] keyManagers = buildKeyManagers(loadKeyStore(), ((String)secret.get(SecretConstants.SERVER_KEY_PASS)).toCharArray());
            TrustManager[] trustManagers;
            if(config.isEnableTwoWayTls()) {
                trustManagers = buildTrustManagers(loadTrustStore());
            } else {
                trustManagers = buildTrustManagers(null);
            }

            SSLContext sslContext;
            sslContext = SSLContext.getInstance("TLSv1");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (Exception e) {
            logger.error("Unable to create SSLContext", e);
            throw new RuntimeException("Unable to create SSLContext", e);
        }
    }

    private static KeyStore loadKeyStore() {
        String name = config.getKeystoreName();
        try (InputStream stream = Config.getInstance().getInputStreamFromFile(name)) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, ((String)secret.get(SecretConstants.SERVER_KEYSTORE_PASS)).toCharArray());
            return loadedKeystore;
        } catch (Exception e) {
            logger.error("Unable to load keystore " + name, e);
            throw new RuntimeException("Unable to load keystore " + name, e);
        }
    }

    protected static KeyStore loadTrustStore() {
        String name = config.getTruststoreName();
        try (InputStream stream = Config.getInstance().getInputStreamFromFile(name)) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, ((String)secret.get(SecretConstants.SERVER_TRUSTSTORE_PASS)).toCharArray());
            return loadedKeystore;
        } catch (Exception e) {
            logger.error("Unable to load truststore " + name, e);
            throw new RuntimeException("Unable to load truststore " + name, e);
        }
    }

    private static TrustManager[] buildTrustManagers(final KeyStore trustStore) {
        TrustManager[] trustManagers = null;
        if (trustStore == null) {
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory
                        .getInstance(KeyManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            }
            catch (NoSuchAlgorithmException | KeyStoreException e) {
                logger.error("Unable to initialise TrustManager[]", e);
                throw new RuntimeException("Unable to initialise TrustManager[]", e);
            }
        }
        else {
            trustManagers = TRUST_ALL_CERTS;
        }
        return trustManagers;
    }

    private static KeyManager[] buildKeyManagers(final KeyStore keyStore, char[] keyPass) {
        KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPass);
            keyManagers = keyManagerFactory.getKeyManagers();
        }
        catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            logger.error("Unable to initialise KeyManager[]", e);
            throw new RuntimeException("Unable to initialise KeyManager[]", e);
        }
        return keyManagers;
    }

}
