package com.networknt.router;

import com.networknt.config.Config;
import com.networknt.service.SingletonServiceFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class HostWhitelistTest {

    private HostWhitelist hostWhitelist;

    @BeforeClass
    public static void setUp() {
        RouterConfig config = (RouterConfig) Config.getInstance()
                .getJsonObjectConfig("router", RouterConfig.class);
        config.setHostWhitelist(new String[] {
                "192.168.0.*",
                "10.1.2.*"
        });
        SingletonServiceFactory.setBean("com.networknt.router.HostWhitelist", new HostWhitelist());
    }

    @Before
    public void init() {
        hostWhitelist = SingletonServiceFactory.getBean(HostWhitelist.class);
    }

    @Test
    public void testHostAllowed() throws URISyntaxException {
        Assert.assertTrue(hostWhitelist.isHostAllowed(new URI("http://192.168.0.1")));
        Assert.assertTrue(hostWhitelist.isHostAllowed(new URI("http://10.1.2.3:8543")));
        Assert.assertTrue(hostWhitelist.isHostAllowed(new URI("https://192.168.0.10:8765")));
    }

    @Test
    public void testHostNotAllowed() throws URISyntaxException {
        Assert.assertFalse(hostWhitelist.isHostAllowed(new URI("http://192.168.2.1")));
        Assert.assertFalse(hostWhitelist.isHostAllowed(new URI("http2://10.2.3.4:8643")));
        Assert.assertFalse(hostWhitelist.isHostAllowed(new URI("https://192.168.1.20:7654")));
    }

}
