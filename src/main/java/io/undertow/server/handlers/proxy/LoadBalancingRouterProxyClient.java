package io.undertow.server.handlers.proxy;

import com.networknt.cluster.Cluster;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.service.SingletonServiceFactory;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.HeaderMap;
import org.xnio.OptionMap;
import org.xnio.ssl.XnioSsl;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.undertow.server.handlers.proxy.ProxyConnectionPool.AvailabilityType.*;

/**
 * This is a proxy client that supports multiple downstream services.
 *
 * @author Steve Hu
 */
public class LoadBalancingRouterProxyClient implements ProxyClient {

    private static final AttachmentKey<AttachmentList<Host>> ATTEMPTED_HOSTS = AttachmentKey.createList(Host.class);
    private static Cluster cluster = SingletonServiceFactory.getBean(Cluster.class);

    /**
     * Time in seconds between retries for problem servers
     */
    private volatile int problemServerRetry = 10;

    /**
     * The number of connections to create per thread
     */
    private volatile int connectionsPerThread = 10;
    private volatile int maxQueueSize = 0;
    private volatile int softMaxConnectionsPerThread = 5;
    private volatile int ttl = -1;

    /**
     * The service hosts list map
     */
    private volatile Map<String, Host[]> hosts = new CopyOnWriteMap<>();

    private final HostSelector hostSelector;
    private final UndertowClient client;

    /**
     * These needs to be come from configuration
     */
    private XnioSsl ssl;
    private OptionMap options;
    private InetSocketAddress bindAddress;

    private static final ProxyTarget PROXY_TARGET = new ProxyTarget() {
    };

    public LoadBalancingRouterProxyClient() {
        this(UndertowClient.getInstance());
    }

    public LoadBalancingRouterProxyClient(UndertowClient client) {
        this(client, null);
    }

    public LoadBalancingRouterProxyClient(UndertowClient client, HostSelector hostSelector) {
        this.client = client;
        if (hostSelector == null) {
            this.hostSelector = new RoundRobinHostSelector();
        } else {
            this.hostSelector = hostSelector;
        }
    }

    public LoadBalancingRouterProxyClient setSsl(final XnioSsl ssl) {
        this.ssl = ssl;
        return this;
    }

    public LoadBalancingRouterProxyClient setOptionMap(final OptionMap options) {
        this.options = options;
        return this;
    }

    public LoadBalancingRouterProxyClient setProblemServerRetry(int problemServerRetry) {
        this.problemServerRetry = problemServerRetry;
        return this;
    }

    public int getProblemServerRetry() {
        return problemServerRetry;
    }

    public int getConnectionsPerThread() {
        return connectionsPerThread;
    }

    public LoadBalancingRouterProxyClient setConnectionsPerThread(int connectionsPerThread) {
        this.connectionsPerThread = connectionsPerThread;
        return this;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public LoadBalancingRouterProxyClient setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        return this;
    }

    public LoadBalancingRouterProxyClient setTtl(int ttl) {
        this.ttl = ttl;
        return this;
    }

    public LoadBalancingRouterProxyClient setSoftMaxConnectionsPerThread(int softMaxConnectionsPerThread) {
        this.softMaxConnectionsPerThread = softMaxConnectionsPerThread;
        return this;
    }

    public synchronized void addHosts(final String serviceId, final String envTag) {
        String key = serviceId + envTag;
        List<URI> uris = cluster.services(ssl == null ? "http" : "https", serviceId, envTag);
        hosts.remove(key);
        Host[] newHosts = new Host[uris.size()];
        for (int i = 0; i < uris.size(); i++) {
            Host h = new Host(serviceId, bindAddress, uris.get(i), ssl, options);
            newHosts[i] = h;
        }
        hosts.put(key, newHosts);
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange exchange) {
        return PROXY_TARGET;
    }

    @Override
    public void getConnection(ProxyTarget target, HttpServerExchange exchange, final ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
        Host host = selectHost(exchange);
        if (host == null) {
            // give it second chance for service discovery again when problem occurs.
            host = selectHost(exchange);
        }
        if (host == null) {
            callback.couldNotResolveBackend(exchange);
        } else {
            exchange.addToAttachmentList(ATTEMPTED_HOSTS, host);
            host.connectionPool.connect(target, exchange, callback, timeout, timeUnit, false);
        }
    }

    protected Host selectHost(HttpServerExchange exchange) {
        // get serviceId, env tag and hash key from header.
        HeaderMap headers = exchange.getRequestHeaders();
        String serviceId = headers.getFirst(HttpStringConstants.SERVICE_ID);
        String envTag = headers.getFirst(HttpStringConstants.ENV_TAG);
        String key = serviceId + envTag;

        AttachmentList<Host> attempted = exchange.getAttachment(ATTEMPTED_HOSTS);
        Host[] hostArray = this.hosts.get(key);
        if (hostArray == null || hostArray.length == 0) {
            // this must be the first this service is called since the router is started. discover here.
            addHosts(serviceId, envTag);
            hostArray = this.hosts.get(key);
        }

        int host = hostSelector.selectHost(hostArray);

        final int startHost = host; //if the all hosts have problems we come back to this one
        Host full = null;
        Host problem = null;
        do {
            Host selected = hostArray[host];
            if (attempted == null || !attempted.contains(selected)) {
                ProxyConnectionPool.AvailabilityType available = selected.connectionPool.available();
                if (available == AVAILABLE) {
                    return selected;
                } else if (available == FULL && full == null) {
                    full = selected;
                } else if ((available == PROBLEM || available == FULL_QUEUE) && problem == null) {
                    problem = selected;
                }
            }
            host = (host + 1) % hostArray.length;
        } while (host != startHost);
        if (full != null) {
            return full;
        }
        if (problem != null) {
            addHosts(serviceId, envTag);
        }
        //no available hosts
        return null;
    }

    /**
     * Should only be used for tests
     * <p>
     * DO NOT CALL THIS METHOD WHEN REQUESTS ARE IN PROGRESS
     * <p>
     * It is not thread safe so internal state can get messed up.
     */
    public void closeCurrentConnections() {
        for (Map.Entry<String, Host[]> entry : hosts.entrySet()) {
            for (Host host : entry.getValue()) {
                host.closeCurrentConnections();
            }
        }
    }

    public final class Host extends ConnectionPoolErrorHandler.SimpleConnectionPoolErrorHandler implements ConnectionPoolManager {
        final ProxyConnectionPool connectionPool;
        final String serviceId;
        final URI uri;
        final XnioSsl ssl;

        private Host(String serviceId, InetSocketAddress bindAddress, URI uri, XnioSsl ssl, OptionMap options) {
            this.connectionPool = new ProxyConnectionPool(this, bindAddress, uri, ssl, client, options);
            this.serviceId = serviceId;
            this.uri = uri;
            this.ssl = ssl;
        }

        @Override
        public int getProblemServerRetry() {
            return problemServerRetry;
        }

        @Override
        public int getMaxConnections() {
            return connectionsPerThread;
        }

        @Override
        public int getMaxCachedConnections() {
            return connectionsPerThread;
        }

        @Override
        public int getSMaxConnections() {
            return softMaxConnectionsPerThread;
        }

        @Override
        public long getTtl() {
            return ttl;
        }

        @Override
        public int getMaxQueueSize() {
            return maxQueueSize;
        }

        public URI getUri() {
            return uri;
        }

        void closeCurrentConnections() {
            connectionPool.closeCurrentConnections();
        }
    }

    public interface HostSelector {

        int selectHost(Host[] availableHosts);
    }

    static class RoundRobinHostSelector implements HostSelector {

        private final AtomicInteger currentHost = new AtomicInteger(0);

        @Override
        public int selectHost(Host[] availableHosts) {
            return currentHost.incrementAndGet() % availableHosts.length;
        }
    }

}
