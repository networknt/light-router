package com.networknt.router.middleware;

import com.networknt.config.Config;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.util.Map;

/**
 * This is a middleware handler that is responsible for getting the JWT access token from
 * OAuth 2.0 provider for the particular router client. The only token that is supported in
 * this handler is client credentials token as there is no user information available here.
 *
 * The client_id will be retrieved from client.yml and client_secret will be retrieved from
 * secret.yml
 *
 * This handler will also responsible for checking if the cached token is about to expired
 * or not. In which case, it will renew the token in another thread. When request comes and
 * the cached token is already expired, then it will block the request and go to the OAuth
 * provider to get the token and then resume the request to the next handler in the chain.
 *
 * The logic is very similar with client module in light-4j but this is implemented in a
 * handler instead.
 *
 * This light-router is designed for standalone or client that is not implemented in Java
 * Otherwise, you should use client module instead of this one. In the future, we might
 * add Authorization Code grant type support by providing an endpoint in the light-router
 * to accept Authorization Code redirect and then get the token from OAuth 2.0 provider.
 *
 * There is no specific configuration file for this handler to enable or disable it. If
 * you want to bypass ths handler, you need to comment it out from service.yml middleware
 * handler section.
 *
 */
public class TokenHandler implements MiddlewareHandler {
    public static final String CONFIG_NAME = "token";
    public static final String ENABLED = "enabled";

    public static Map<String, Object> config = Config.getInstance().getJsonMapConfigNoCache(CONFIG_NAME);

    private volatile HttpHandler next;

    public TokenHandler() {
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        // check if there is a bear token in the authorization header in the request.

    }

    @Override
    public HttpHandler getNext() {
        return next;
    }

    @Override
    public MiddlewareHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    @Override
    public boolean isEnabled() {
        Object object = config.get(ENABLED);
        return object != null && (Boolean) object;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(TokenHandler.class.getName(), config, null);
    }
}
