package com.networknt.router;

import com.networknt.config.Config;
import com.networknt.config.ConfigException;

import java.net.URI;
import java.util.Arrays;

public class HostWhitelist {

    private static final RouterConfig config = (RouterConfig) Config.getInstance()
            .getJsonObjectConfig("router", RouterConfig.class);

    public boolean isHostAllowed(URI serviceUri) {
        if (serviceUri != null) {
            String[] hostWhitelist = config.getHostWhitelist();
            if (hostWhitelist == null || hostWhitelist.length == 0) {
                throw new ConfigException("No whitelist defined to allow the route to " + serviceUri);
            }
            String host = serviceUri.getHost();
            return Arrays.stream(config.getHostWhitelist()).anyMatch(
                    hostRegEx -> host != null && host.matches(hostRegEx));
        } else {
            return false;
        }
    }

}
