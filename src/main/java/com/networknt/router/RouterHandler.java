package com.networknt.router;

import com.networknt.config.Config;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;

/**
 * This is a wrapper class for ProxyHandler as it is implemented as final. This class implements
 * the HttpHandler which can be injected into the handler.yml configuration file as another option
 * for the handlers injection. The other option is to use RouterHandlerProvider in service.yml file.
 *
 * @author Steve Hu
 */
public class RouterHandler implements HttpHandler {
    static final String CONFIG_NAME = "router";
    static RouterConfig config = (RouterConfig)Config.getInstance().getJsonObjectConfig(CONFIG_NAME, RouterConfig.class);

    ProxyHandler proxyHandler;

    public RouterHandler() {
        // As we are building a client side router for the light platform, the assumption is the server will
        // be on HTTP 2.0 TSL always. No need to handle HTTP 1.1 case here.
        RouterProxyClient routerProxyClient = new RouterProxyClient();
        proxyHandler = ProxyHandler.builder()
                .setProxyClient(routerProxyClient)
                .setMaxConnectionRetries(config.maxConnectionRetries)
                .setMaxRequestTime(config.maxRequestTime)
                .setReuseXForwarded(config.reuseXForwarded)
                .setRewriteHostHeader(config.rewriteHostHeader)
                .setNext(ResponseCodeHandler.HANDLE_404)
                .build();
    }

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
        proxyHandler.handleRequest(httpServerExchange);
    }
}
