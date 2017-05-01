package io.github.gsteckman.rpi_rest;

/*
 * SsdpHandler.java
 * 
 * Copyright 2017 Greg Steckman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing permissions and limitations under the License.
 *
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.eaio.uuid.UUID;
import com.nls.net.ssdp.SsdpPacket;
import com.nls.net.ssdp.SsdpPacketListener;
import com.nls.net.ssdp.SsdpService;

/**
 * This class implements an SSDP endpoint that responds to M-SEARCH broadcast messages
 * and periodically transmits NOTIFY messages as defined in the UPnP Device Architecture 1.1.
 *  
 * @see http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf 
 * 
 * @author Greg Steckman
 *
 */
public class SsdpHandler implements SsdpPacketListener {
    private static final Log LOG = LogFactory.getLog(SsdpHandler.class);
    private static final String ST = "urn:gsteckman-github-io:device:rpi:1"; // search target
    private static final int MAX_AGE = 1800; // UPnP cache-control max age in seconds
    private static final long BOOTID = System.currentTimeMillis();
    private static final long CONFIGID = 1;
    private static final String UUID_KEY = "UUID";
    private static final int MULTICAST_PORT = 1900;
    private static final String MULTICAST_ADDRESS = "239.255.255.250";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(SsdpHandler.class);
    private static final SsdpHandler INSTANCE = new SsdpHandler();
    private static final int TTL = 2;
    private SsdpService svc;
    private InetAddress serverAddress = null;
    private MulticastSocket notifySocket;
    private Timer notifyTimer;

    /** 
     * @return The instance of this class.
     */
    public static SsdpHandler getInstance() {
        return INSTANCE;
    }

    /**
     * Constructs a new instance of this class.
     */
    private SsdpHandler() {
        LOG.info("Instantiating SsdpHandler");

        try {
            // Use first IPv4 address that isn't loopback, any, or link local as the server address
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements() && serverAddress == null) {
                NetworkInterface i = interfaces.nextElement();
                Enumeration<InetAddress> addresses = i.getInetAddresses();
                while (addresses.hasMoreElements() && serverAddress == null) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        if (!address.isAnyLocalAddress() && !address.isLinkLocalAddress()
                                && !address.isLoopbackAddress() && !address.isMulticastAddress()) {
                            serverAddress = address;
                        }
                    }
                }
            }

            if (serverAddress == null) {
                LOG.warn("Server address unknown");
            }

            svc = SsdpService.forAllMulticastAvailableNetworkInterfaces(this);
            svc.listen();

            // setup Multicast for Notify messages
            notifySocket = new MulticastSocket();
            notifySocket.setTimeToLive(TTL);
            notifyTimer = new Timer("UPnP Notify Timer", true);
            notifyTimer.scheduleAtFixedRate(new NotifySender(), 5000, MAX_AGE * 1000 / 2);
        } catch (Exception e) {
            LOG.error("SsdpHandler in unknown state due to exception in constructor.", e);
        }
    }

    /**
     * Implements the SsdpPacketListener interface to respond to M-SEARCH messages.
     */
    public void received(final SsdpPacket pkt) {
        LOG.debug(pkt);
        LOG.debug(pkt.getMessage());
        Map<String, String> headers = pkt.getMessage().getHeaders();
        String st = headers.get("ST");
        if (ST.equals(st)) {
            sendResponse(pkt.getSocketAddress());
        }
    }

    /**
     * Retrieves a previously generated UUID from the Preferences store, and if none exists creates a new UUID and saves it in the Preferences store.
     * 
     * @return A UUID
     */
    private UUID getUuid() {
        UUID id;
        String u = PREFERENCES.get(UUID_KEY, null);

        if (u == null) {
            id = new UUID();
            PREFERENCES.put(UUID_KEY, id.toString());
        } else {
            id = new UUID(u);
        }
        return id;
    }

    private void sendResponse(final SocketAddress addr) {
        if (!(addr instanceof InetSocketAddress)) {
            LOG.warn("Don't know how to handle non Internet addresses");
            return;
        }
        DatagramSocket sock = null;
        LOG.debug("Responding to " + addr.toString());

        try {
            sock = new DatagramSocket();
            sock.connect(addr);
            byte[] ba = generateSearchResponse().getBytes();
            sock.send(new DatagramPacket(ba, ba.length));
        } catch (IOException e) {
            LOG.error(e.getMessage());
        } finally {
            if (sock != null) {
                sock.close();
            }
        }
    }

    private String getServerAddress() {
        if (serverAddress == null) {
            return "127.0.0.1";
        } else {
            return serverAddress.getHostAddress();
        }
    }

    private String generateSearchResponse() throws IOException {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.print("HTTP/1.1 200 OK\r\n");
        pw.printf("CACHE-CONTROL: max-age=%d\r\n", MAX_AGE);
        pw.print("EXT:\r\n");
        pw.printf("LOCATION: http://%s:8080\r\n", getServerAddress());
        pw.print("SERVER: " + System.getProperty("os.name") + "/" + System.getProperty("os.version")
                + ", UPnP/1.1, rpi-rest/0.1\r\n");
        pw.printf("ST: %s\r\n", ST);
        pw.printf("USN: uuid:%s\r\n", getUuid().toString());
        pw.printf("BOOTID.UPNP.ORG: %d\r\n", BOOTID);
        pw.printf("CONFIGID.UPNP.ORG: %d\r\n", CONFIGID);
        pw.printf("\r\n");
        pw.flush();
        String resp = sw.toString();
        pw.close();
        sw.close();
        return resp;
    }

    private String generateNotify() throws IOException {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.printf("NOTIFY * HTTP/1.1\r\n");
        pw.printf("HOST: %s:%d\r\n", MULTICAST_ADDRESS, MULTICAST_PORT);
        pw.printf("CACHE-CONTROL: max-age=%d\r\n", MAX_AGE);
        pw.printf("LOCATION: http://%s:8080\r\n", getServerAddress());
        pw.printf("NT: %s\r\n", ST);
        pw.printf("NTS: ssdp:alive\r\n");
        pw.print("SERVER: " + System.getProperty("os.name") + "/" + System.getProperty("os.version")
                + ", UPnP/1.1, rpi-rest/0.1\r\n");
        pw.printf("USN: uuid:%s\r\n", getUuid().toString());
        pw.printf("BOOTID.UPNP.ORG: %d\r\n", BOOTID);
        pw.printf("CONFIGID.UPNP.ORG: %d\r\n", CONFIGID);
        pw.printf("\r\n");
        pw.flush();
        String resp = sw.toString();
        pw.close();
        sw.close();
        return resp;
    }

    private class NotifySender extends TimerTask {
        private byte[] ba;
        DatagramPacket pkt;

        public NotifySender() throws IOException {
            ba = generateNotify().getBytes();
            pkt = new DatagramPacket(ba, ba.length, InetAddress.getByName(MULTICAST_ADDRESS), MULTICAST_PORT);
        }

        public void run() {
            try {
                for(int i = 0; i< 3; i++){
                    notifySocket.send(pkt);
                    try{
                        Thread.sleep(1000);
                    }catch(InterruptedException e){
                        return;
                    }
                }
            } catch (IOException e) {
                LOG.warn("Exception in sendNotify", e);
            }
        }

    }

    /**
     * Closes sockets, frees resources and terminates threads. Called by Spring Framework prior to destroying the bean.
     */
    @PreDestroy
    public void close() {
        LOG.info("closing SsdpHandler");
        svc.close();
        notifyTimer.cancel();
        notifySocket.close();
    }
}
