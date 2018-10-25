package com.networknt.router.middleware;

import com.networknt.client.oauth.*;
import com.networknt.common.DecryptUtil;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.networknt.client.Http2Client.CONFIG_SECRET;

/**
 * This is a middleware handler that is responsible for getting a JWT access token from
 * OAuth 2.0 provider for the particular router client. In this use case it is assumed the client
 * has a signed saml and a signed JWT token in the incoming HTTP headers. These two tokens will be
 * passed to the authorization server to get the JWT access token.
 * This handler will also be responsible for checking if the cached token is about to expired
 * or not. In which case, it will renew the token in another thread. When request comes and
 * the cached token is already expired, then it will block the request and go to the OAuth
 * provider to get a new token and then resume the request to the next handler in the chain.
 *
 * This handler is very similar to the TokenHandler except it doesn't use the client credential
 * grant type.
 *
 */
public class SAMLTokenHandler implements MiddlewareHandler {
    public static final String CONFIG_NAME = "token";
    public static final String CLIENT_CONFIG_NAME = "client";
    public static final String ENABLED = "enabled";
    public static final String CONFIG_SECURITY = "security";

    public static Map<String, Object> config = Config.getInstance().getJsonMapConfigNoCache(CONFIG_NAME);
    static Logger logger = LoggerFactory.getLogger(SAMLTokenHandler.class);
    private volatile HttpHandler next;

    private String jwt;    // the cached jwt token for client credentials grant type
    private long expire;   // jwt expire time in millisecond so that we don't need to parse the jwt.

    static final String OAUTH = "oauth";
    static final String TOKEN = "token";
    static final String OAUTH_HTTP2_SUPPORT = "oauthHttp2Support";

    static final String SAMLAssertionHeader = "assertion";
    static final String JWTAssertionHeader = "client_assertion";

    static final String STATUS_SAMLBEARER_CREDENTIALS_TOKEN_NOT_AVAILABLE = "ERR10009";

    static Map<String, Object> clientConfig;
    static Map<String, Object> tokenConfig;
    static Map<String, Object> secretConfig;
    static boolean oauthHttp2Support;

    private final Object lock = new Object();

    public SAMLTokenHandler() {
        clientConfig = Config.getInstance().getJsonMapConfig(CLIENT_CONFIG_NAME);
        if(clientConfig != null) {
            Map<String, Object> oauthConfig = (Map<String, Object>)clientConfig.get(OAUTH);
            if(oauthConfig != null) {
                tokenConfig = (Map<String, Object>)oauthConfig.get(TOKEN);
            }
        }
        Map<String, Object> securityConfig = Config.getInstance().getJsonMapConfig(CONFIG_SECURITY);
        if(securityConfig != null) {
            Boolean b = (Boolean)securityConfig.get(OAUTH_HTTP2_SUPPORT);
            oauthHttp2Support = (b == null ? false : b.booleanValue());
        }

        Map<String, Object> secretMap = Config.getInstance().getJsonMapConfig(CONFIG_SECRET);
        if(secretMap != null) {
            secretConfig = DecryptUtil.decryptMap(secretMap);
        } else {
            throw new ExceptionInInitializerError("Could not locate secret.yml");
        }
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        // check if there is a bear token in the authorization header in the request. If this
        // is one, then this must be the subject token that is linked to the original user.
        // We will keep this token in the Authorization header but create a new token with
        // client credentials grant type with scopes for the particular client. (Can we just
        // assume that the subject token has the scope already?)
        logger.debug(exchange.toString());
        getSAMLBearerToken(exchange.getRequestHeaders().getFirst(SAMLAssertionHeader), exchange.getRequestHeaders().getFirst(JWTAssertionHeader));

        exchange.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer " + jwt);
        exchange.getRequestHeaders().remove(SAMLAssertionHeader);
        exchange.getRequestHeaders().remove(JWTAssertionHeader);
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
        ModuleRegistry.registerModule(SAMLTokenHandler.class.getName(), config, null);
    }


    private void getSAMLBearerToken(String samlAssertion , String jwtAssertion) throws ClientException {
        SAMLBearerRequest tokenRequest = new SAMLBearerRequest(samlAssertion , jwtAssertion);
        TokenResponse tokenResponse = OauthHelper.getTokenFromSaml(tokenRequest);
        synchronized (lock) {
            jwt = tokenResponse.getAccessToken();
            // the expiresIn is seconds and it is converted to millisecond in the future.
            expire = System.currentTimeMillis() + tokenResponse.getExpiresIn() * 1000;
            logger.info("Get client credentials token {} with expire_in {} seconds", jwt, tokenResponse.getExpiresIn());
        }
    }
}
