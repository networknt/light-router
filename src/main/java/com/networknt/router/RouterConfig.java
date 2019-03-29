package com.networknt.router;

/**
 * Config class for reverse router.
 *
 * @author Steve Hu
 */
public class RouterConfig {
    boolean http2Enabled;
    boolean httpsEnabled;
    int maxRequestTime;
    boolean rewriteHostHeader;
    boolean reuseXForwarded;
    int maxConnectionRetries;
    String[] hostWhitelist;

    public RouterConfig() {
    }

    public boolean isHttp2Enabled() {
        return http2Enabled;
    }

    public void setHttp2Enabled(boolean http2Enabled) {
        this.http2Enabled = http2Enabled;
    }

    public boolean isHttpsEnabled() {
        return httpsEnabled;
    }

    public void setHttpsEnabled(boolean httpsEnabled) {
        this.httpsEnabled = httpsEnabled;
    }

    public int getMaxRequestTime() {
        return maxRequestTime;
    }

    public void setMaxRequestTime(int maxRequestTime) {
        this.maxRequestTime = maxRequestTime;
    }

    public boolean isRewriteHostHeader() { return rewriteHostHeader; }

    public void setRewriteHostHeader(boolean rewriteHostHeader) { this.rewriteHostHeader = rewriteHostHeader; }

    public boolean isReuseXForwarded() { return reuseXForwarded; }

    public void setReuseXForwarded(boolean reuseXForwarded) { this.reuseXForwarded = reuseXForwarded; }

    public int getMaxConnectionRetries() { return maxConnectionRetries; }

    public void setMaxConnectionRetries(int maxConnectionRetries) { this.maxConnectionRetries = maxConnectionRetries; }

    public String[] getHostWhitelist() {
        return hostWhitelist;
    }

    public void setHostWhitelist(String[] hostWhitelist) {
        this.hostWhitelist = hostWhitelist;
    }
}
