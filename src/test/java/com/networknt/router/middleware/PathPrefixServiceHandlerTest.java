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

package com.networknt.router.middleware;

import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.networknt.header.HeaderHandler;
import com.networknt.httpstring.HttpStringConstants;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Methods;

public class PathPrefixServiceHandlerTest extends BaseServiceHandlerTest{
    static final Logger logger = LoggerFactory.getLogger(PathPrefixServiceHandlerTest.class);

    static Undertow server = null;

    @BeforeClass
    public static void setUp() {
        if(server == null) {
            logger.info("starting server");
            HttpHandler handler = getTestHandler();
            PathPrefixServiceHandler pathServiceHandler = new PathPrefixServiceHandler();
            pathServiceHandler.setNext(handler);
            handler = pathServiceHandler;
            HeaderHandler headerHandler = new HeaderHandler();
            headerHandler.setNext(handler);
            handler = headerHandler;
            server = Undertow.builder()
                    .addHttpListener(7080, "localhost")
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
            result.put(path, HandlerUtils.findServiceId(HandlerUtils.normalisePath(path), PathPrefixServiceHandler.mapping));
        }

        Assert.assertEquals(expected, result);
    }
}
