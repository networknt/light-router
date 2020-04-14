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
import com.networknt.cluster.Cluster;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.service.SingletonServiceFactory;
import com.networknt.utility.Constants;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.HeaderMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This is a simple proxy client for light-router and it can discover and load balance
 * the hosts from Consul or other service discovery services supported by the light platform.
 *
 * As the target servers are implemented in light-4j which is HTTP 2.0 supported, there is no
 * need to have a connection pool to be implemented. For each host, there would be only one
 * connection as HTTP 2.0 supports multiplexing.
 *
 * @author Steve Hu
 *
 */
@Deprecated
public class RouterProxyClient implements ProxyClient {
    // For every unique serviceId the client is connecting to, there is a Connection that
    // connects to one of the target servers discovered from Consul etc. The corresponding
    // connection might be changed due to the current one is closed or timed out.
    static Map<String, ClientConnection> connectionMap = new CopyOnWriteMap<>();
    static Cluster cluster = SingletonServiceFactory.getBean(Cluster.class);
    static Logger logger = LoggerFactory.getLogger(RouterProxyClient.class);

    private final Http2Client client;
    private static final ProxyTarget PROXY_TARGET = new ProxyTarget() {};
    private static final HostWhitelist HOST_WHITELIST = new HostWhitelist();

    public RouterProxyClient() {
        client = Http2Client.getInstance();
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange httpServerExchange) {
        return PROXY_TARGET;
    }

    @Override
    public void getConnection(ProxyTarget target, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
        // get serviceId, env tag and hash key from header.
        HeaderMap headers = exchange.getRequestHeaders();
        String serviceId = headers.getFirst(HttpStringConstants.SERVICE_ID);
        String serviceUrl = headers.getFirst(HttpStringConstants.SERVICE_URL);
        if (serviceUrl != null) {
            logger.debug(String.format("Found %s in request header: %s", "service_url", serviceUrl));
        }
        String envTag = headers.getFirst(HttpStringConstants.ENV_TAG);
        String key = (serviceUrl != null ? serviceUrl : serviceId) + envTag;
        // base on that try to lookup a connection in connectionMap, create a new one if it
        ClientConnection existing = connectionMap.get(key);
        if (existing != null) {
            if (existing.isOpen()) {
                // This connection already has a client, re-use it.
                callback.completed(exchange, new ProxyConnection(existing, "/"));
                return;
            } else {
                // The existing connection is timeout or closed by the server.
                connectionMap.remove(key);
            }
        }
        // doesn't exist or it is closed already. discovery here.
        String host = serviceUrl != null ? serviceUrl : cluster.serviceToUrl(Constants.HTTPS, serviceId, envTag, null);
        try {
            URI uri = new URI(host);
            if (serviceUrl != null) {
                if (HOST_WHITELIST != null) {
                    if (HOST_WHITELIST.isHostAllowed(uri)) {
                        client.connect(new RouterProxyClient.ConnectNotifier(callback, exchange), uri, Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true));
                    } else {
                        exchange.setReasonPhrase(String.format("Route to %s is not allowed in the host whitelist", serviceUrl));
                        callback.failed(exchange);
                    }
                } else {
                    exchange.setReasonPhrase(String.format("Host Whitelist must be enabled to support route based on %s in Http header",
                            HttpStringConstants.SERVICE_URL));
                    callback.failed(exchange);
                }
            } else {
                client.connect(new RouterProxyClient.ConnectNotifier(callback, exchange), uri, Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true));
            }
        } catch (URISyntaxException e) {
            logger.error("Invalid URI:" + host, e);
            exchange.setReasonPhrase("Invalid URI:" + host);
            callback.failed(exchange);
        }
    }

    private static final class ConnectNotifier implements ClientCallback<ClientConnection> {
        private final ProxyCallback<ProxyConnection> callback;
        private final HttpServerExchange exchange;

        private ConnectNotifier(ProxyCallback<ProxyConnection> callback, HttpServerExchange exchange) {
            this.callback = callback;
            this.exchange = exchange;
        }

        @Override
        public void completed(final ClientConnection connection) {
            if(logger.isDebugEnabled()) logger.debug("ConnectNotifier completed is called with connection " + connection);
            // put connection into the connectionMap so that it can be reused.
            HeaderMap headers = exchange.getRequestHeaders();
            String serviceId = headers.getFirst(HttpStringConstants.SERVICE_ID);
            String serviceUrl = headers.getFirst(HttpStringConstants.SERVICE_URL);
            String envTag = headers.getFirst(HttpStringConstants.ENV_TAG);
            if (serviceId != null || serviceUrl != null) {
                String key = (serviceUrl != null ? serviceUrl : serviceId) + envTag;
                connectionMap.put(key, connection);
            }
            callback.completed(exchange, new ProxyConnection(connection, "/"));
        }

        @Override
        public void failed(IOException e) {
            if(logger.isDebugEnabled()) logger.debug("ConnectNotifier failed is called with exception", e);
            callback.failed(exchange);
        }
    }
}
