package com.networknt.router.middleware;

import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * When using router, each request must have serviceId in the header in order to allow router
 * to do the service discovery before invoke downstream service. The reason we have to do that
 * is due to the unpredictable path between services. If you are sure that all the downstream
 * services can be identified by a unique path prefix, then you can use this Path to ServiceId
 * mapper handler to uniquely identify the serviceId and put it into the header. In this case,
 * the client can invoke the service just the same way it is invoking the service directly.
 * <p>
 * Please note that you cannot invoke /health or /server/info endpoints as these are the common
 * endpoints injected by the framework and all services will have them on the same path. The
 * router cannot figure out which service you want to invoke so an error message will be returned
 * <p>
 * Unlike {@link PathServiceHandler}, this handler does not require OpenAPIHandler or SwaggerHandler
 * but is also unable to do any validation beyond the path prefix.
 *
 * @author Logi Ragnarsson <logi@logi.org>
 * @author Steve Hu
 */
public class PathPrefixServiceHandler implements MiddlewareHandler {
    public static final String CONFIG_NAME = "pathPrefixService";
    public static final String ENABLED = "enabled";
    public static final String MAPPING = "mapping";

    public static Map<String, Object> config = Config.getInstance().getJsonMapConfigNoCache(CONFIG_NAME);
    public static Map<String, String> mapping = (Map<String, String>)config.get(MAPPING);
    static Logger logger = LoggerFactory.getLogger(PathPrefixServiceHandler.class);
    private volatile HttpHandler next;

    static final String STATUS_INVALID_REQUEST_PATH = "ERR10007";

    public PathPrefixServiceHandler() {
        logger.info("PathServiceHandler is constructed");
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        HeaderValues serviceIdHeader = exchange.getRequestHeaders().get(Constants.SERVICE_ID);
        String serviceId = serviceIdHeader != null ? serviceIdHeader.peekFirst() : null;
        if(serviceId == null) {
            String requestPath = exchange.getRequestURI();
            serviceId = findServiceId(requestPath);
            if(serviceId == null) {
                setExchangeStatus(exchange, STATUS_INVALID_REQUEST_PATH, requestPath);
                return;
            }
            exchange.getRequestHeaders().put(Constants.SERVICE_ID, serviceId);
        }
        Handler.next(exchange, next);
    }

    /**
     * Looks up the appropriate serviceId for a given requestPath taken directly from exchange.
     * Returns null if the path does not map to a configured service.
     */
    String findServiceId(String requestPath) {
        requestPath = normalisePath(requestPath);
        if(logger.isDebugEnabled()) logger.debug("findServiceId for " + requestPath);
        String serviceId = null;

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String prefix = entry.getKey();
            if(requestPath.startsWith(prefix)) {
                if((requestPath.length() == prefix.length() || requestPath.charAt(prefix.length()) == '/')) {
                    serviceId = entry.getValue();
                    break;
                }
            }
        }
        if(logger.isDebugEnabled()) logger.debug("serviceId = " + serviceId);
        return serviceId;
    }

    private static String normalisePath(String requestPath) {
        if(!requestPath.startsWith("/")) {
            return "/" + requestPath;
        }
        return requestPath;
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
        return object != null && (Boolean)object;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(TokenHandler.class.getName(), config, null);
    }

}
