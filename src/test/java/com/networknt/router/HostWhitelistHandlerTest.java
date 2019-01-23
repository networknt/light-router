package com.networknt.router;

import com.networknt.config.Config;
import com.networknt.service.SingletonServiceFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class HostWhitelistHandlerTest {

    private HostWhitelistHandler hostWhitelistHandler;

    @BeforeClass
    public static void setUp() {
        RouterConfig config = (RouterConfig) Config.getInstance()
                .getJsonObjectConfig("router", RouterConfig.class);
        config.setHostWhitelist(new String[] {
                "192.168.0.*"
        });
        SingletonServiceFactory.setBean("com.networknt.router.HostWhitelistHandler", new HostWhitelistHandler());
    }

    @Before
    public void init() {
        hostWhitelistHandler = SingletonServiceFactory.getBean(HostWhitelistHandler.class);
    }

    @Test
    public void testHostAllowed() throws URISyntaxException {
        Assert.assertTrue(hostWhitelistHandler.isHostAllowed(new URI("http://192.168.0.1")));
    }

    @Test
    public void testHostNotAllowed() throws URISyntaxException {
        Assert.assertFalse(hostWhitelistHandler.isHostAllowed(new URI("http://192.168.2.1")));
    }

}
