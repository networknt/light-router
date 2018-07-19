package com.networknt.router.middleware;

import com.networknt.audit.AuditHandler;
import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * When using router, each request must have serviceId in the header in order to allow router
 * to do the service discovery before invoke downstream service. The reason we have to do that
 * is due to the unpredictable path between services. If you are sure that all the downstream
 * services can be identified by the path, then you can use this Path to ServiceId mapper handler
 * to uniquely identify the serviceId and put it into the header. In this case, the client can
 * invoke the service just the same way it is invoking the service directly.
 *
 * Please note that you cannot invoke /health or /server/info endpoints as these are the common
 * endpoints injected by the framework and all services will have them on the same path. The
 * router cannot figure out which service you want to invoke so an error message will be returned
 *
 * This handler depends on OpenAPIHandler or SwaggerHandler in light-rest-4j framework. That means
 * this handler only works with RESTful APIs. In rest swagger-meta or openapi-meta, the endpoint
 * of each request is saved into auditInfo object which is attached to the exchange for auditing.
 *
 * @author Steve Hu
 *
 */
public class PathServiceHandler implements MiddlewareHandler {
    public static final String CONFIG_NAME = "pathService";
    public static final String ENABLED = "enabled";
    public static final String MAPPING = "mapping";

    public static Map<String, Object> config = Config.getInstance().getJsonMapConfigNoCache(CONFIG_NAME);
    public static Map<String, String> mapping = (Map<String, String>)config.get(MAPPING);
    static Logger logger = LoggerFactory.getLogger(PathServiceHandler.class);
    private volatile HttpHandler next;

    static final String AUDIT_INFO_NOT_FOUND = "ERR10041";

    public PathServiceHandler() {
        logger.info("PathServiceHandler is constructed");
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        Map<String, Object> auditInfo = exchange.getAttachment(AuditHandler.AUDIT_INFO);
        if(auditInfo != null) {
            String endpoint = (String)auditInfo.get(Constants.ENDPOINT_STRING);
            if(logger.isDebugEnabled()) logger.debug("endpoint = " + endpoint);
            // now find the mapped serviceId from the mapping.
            if(endpoint != null) {
                String serviceId = mapping.get(endpoint);
                exchange.getRequestHeaders().put(Constants.SERVICE_ID, serviceId);
            }
        } else {
            // couldn't find auditInfo object in exchange attachment.
            setExchangeStatus(exchange, AUDIT_INFO_NOT_FOUND);
            return;
        }
        Handler.next(exchange, next);
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
