package io.github.gsteckman.rpi_rest;

import java.net.URL;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;

public class SubscriptionManagerTest {
    
    @Test
    public void testParseCallbackHeader() {
        SubscriptionManager sm = new SubscriptionManager();
        String hdr1 = "<http://test1.com>";
        String hdr2 = "<http://test1.com><http://test2.com>";

        List<URL> list = sm.parseCallbackHeader(hdr1);
        Assert.assertEquals(1, list.size());
        
        list = sm.parseCallbackHeader(hdr2);
        Assert.assertEquals(2, list.size());
    }

}
