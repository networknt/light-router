package com.networknt.router.middleware;

import com.networknt.client.oauth.*;
import com.networknt.common.DecryptUtil;
import com.networknt.config.Config;
import com.networknt.exception.ApiException;
import com.networknt.exception.ClientException;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Map;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
 * <p>
 * This handler is very similar to the TokenHandler except it doesn't use the client credential
 * grant type.
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
    private String hashSaml;  // the cached hashSaml token for client credentials grant type
    private long expire;   // jwt expire time in millisecond so that we don't need to parse the jwt.

    private volatile boolean renewing = false;
    private volatile long expiredRetryTimeout;
    private volatile long earlyRetryTimeout;

    static final String OAUTH = "oauth";
    static final String TOKEN = "token";
    static final String OAUTH_HTTP2_SUPPORT = "oauthHttp2Support";
    static final String SAMLAssertionHeader = "assertion";
    static final String JWTAssertionHeader = "client_assertion";
    static final String TOKEN_RENEW_BEFORE_EXPIRED = "tokenRenewBeforeExpired";
    static final String EXPIRED_REFRESH_RETRY_DELAY = "expiredRefreshRetryDelay";
    static final String EARLY_REFRESH_RETRY_DELAY = "earlyRefreshRetryDelay";

    static final String STATUS_CLIENT_CREDENTIALS_TOKEN_NOT_AVAILABLE = "ERR10009";

    static final String STATUS_SAMLBEARER_CREDENTIALS_TOKEN_NOT_AVAILABLE = "ERR10009";

    static Map<String, Object> clientConfig;
    static Map<String, Object> tokenConfig;
    static Map<String, Object> secretConfig;
    static boolean oauthHttp2Support;

    private final Object lock = new Object();

    public SAMLTokenHandler() {
        clientConfig = Config.getInstance().getJsonMapConfig(CLIENT_CONFIG_NAME);
        if (clientConfig != null) {
            Map<String, Object> oauthConfig = (Map<String, Object>) clientConfig.get(OAUTH);
            if (oauthConfig != null) {
                tokenConfig = (Map<String, Object>) oauthConfig.get(TOKEN);
            }
        }
        Map<String, Object> securityConfig = Config.getInstance().getJsonMapConfig(CONFIG_SECURITY);
        if (securityConfig != null) {
            Boolean b = (Boolean) securityConfig.get(OAUTH_HTTP2_SUPPORT);
            oauthHttp2Support = (b == null ? false : b.booleanValue());
        }

        Map<String, Object> secretMap = Config.getInstance().getJsonMapConfig(CONFIG_SECRET);
        if (secretMap != null) {
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

        getJWTToken(exchange.getRequestHeaders().getFirst(SAMLAssertionHeader), exchange.getRequestHeaders().getFirst(JWTAssertionHeader));

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


    private void getJwtBearerToken(String samlAssertion, String jwtAssertion) throws ClientException {
        SAMLBearerRequest tokenRequest = new SAMLBearerRequest(samlAssertion, jwtAssertion);
        TokenResponse tokenResponse = OauthHelper.getTokenFromSaml(tokenRequest);
        synchronized (lock) {
            jwt = tokenResponse.getAccessToken();
            // the expiresIn is seconds and it is converted to millisecond in the future.
            expire = System.currentTimeMillis() + tokenResponse.getExpiresIn() * 1000;
            logger.debug("Get client credentials token {} with expire_in {} seconds", jwt, tokenResponse.getExpiresIn());
        }
    }

    //Converting samlAssertion into SAH256 hash.
    private String getHashSaml(String samlAssertion) {
        String samlToken = null;

        if (samlAssertion == null) {
            return samlToken;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(samlAssertion.getBytes(StandardCharsets.UTF_8));
            samlToken = Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Invalid samlAssertion :", samlAssertion, e);
            e.printStackTrace();
        }
        return samlToken;
    }

    private void getJWTToken(String samlAssertion, String jwtAssertion) throws ClientException, ApiException {

        String samlToken = getHashSaml(samlAssertion);

        if (hashSaml != null && hashSaml.equals(samlToken)) {
            logger.debug("Checking JwtTokenExpired..");
            checkJwtTokenExpired(samlAssertion, jwtAssertion);
        } else {
            //Make a call to the Oauth server get a new JWT bearer token;
            getJwtBearerToken(samlAssertion, jwtAssertion);
            hashSaml = samlToken;
            logger.debug("New samltoken :" + hashSaml);
        }

    }


    private void checkJwtTokenExpired(String samlAssertion, String jwtAssertion) throws ClientException, ApiException {

        long tokenRenewBeforeExpired = (Integer) tokenConfig.get(TOKEN_RENEW_BEFORE_EXPIRED);
        long expiredRefreshRetryDelay = (Integer) tokenConfig.get(EXPIRED_REFRESH_RETRY_DELAY);
        long earlyRefreshRetryDelay = (Integer) tokenConfig.get(EARLY_REFRESH_RETRY_DELAY);

        boolean isInRenewWindow = expire - System.currentTimeMillis() < tokenRenewBeforeExpired;

        logger.trace("isInRenewWindow = " + isInRenewWindow);

        if (isInRenewWindow) {
            if (expire <= System.currentTimeMillis()) {
                logger.trace("In renew window and token is expired.");
                // block other request here to prevent using expired token.
                synchronized (TokenHandler.class) {
                    if (expire <= System.currentTimeMillis()) {
                        logger.trace("Within the synch block, check if the current request need to renew token");
                        if (!renewing || System.currentTimeMillis() > expiredRetryTimeout) {
                            // if there is no other request is renewing or the renewing flag is true but renewTimeout is passed
                            renewing = true;
                            expiredRetryTimeout = System.currentTimeMillis() + expiredRefreshRetryDelay;
                            logger.trace("Current request is renewing token synchronously as token is expired already");
                            getJwtBearerToken(samlAssertion, jwtAssertion);
                            renewing = false;
                        } else {
                            logger.trace("Circuit breaker is tripped and not timeout yet!");
                            // reject all waiting requests by thrown an exception.
                            throw new ApiException(new Status(STATUS_CLIENT_CREDENTIALS_TOKEN_NOT_AVAILABLE));
                        }
                    }
                }
            } else {
                // Not expired yet, try to renew async but let requests use the old token.
                logger.trace("In renew window but token is not expired yet.");
                synchronized (TokenHandler.class) {
                    if (expire > System.currentTimeMillis()) {
                        if (!renewing || System.currentTimeMillis() > earlyRetryTimeout) {
                            renewing = true;
                            earlyRetryTimeout = System.currentTimeMillis() + earlyRefreshRetryDelay;
                            logger.trace("Retrieve token async is called while token is not expired yet");

                            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

                            executor.schedule(() -> {
                                try {
                                    getJwtBearerToken(samlAssertion, jwtAssertion);
                                    renewing = false;
                                    logger.trace("Async get token is completed.");
                                } catch (Exception e) {
                                    logger.error("Async retrieve token error", e);
                                    // swallow the exception here as it is on a best effort basis.
                                }
                            }, 50, TimeUnit.MILLISECONDS);
                            executor.shutdown();
                        }
                    }
                }
            }
        }
        logger.trace("Check secondary token is done!");
    }
}
