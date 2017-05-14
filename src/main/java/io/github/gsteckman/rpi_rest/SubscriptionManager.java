package io.github.gsteckman.rpi_rest;

/*
 * SubscriptionManager.java
 * 
 * Copyright 2017 Greg Steckman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance 
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License 
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing permissions and limitations under the License.
 *
 */

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.eaio.uuid.UUID;

/**
 * This class processes and manages UPnP subscriptions. It is to be used in conjunction with one or more HTTP Servlets.
 * Subscription to more than one resource is managed through use of a key that identifies the resource.
 *
 */
public class SubscriptionManager {
    private static final Log LOG = LogFactory.getLog(SubscriptionManager.class);
    private static final long DEFAULT_TIMEOUT = 3600000; // ms
    private Map<String, Map<UUID, SubscriptionInfo>> subscriptions = Collections
            .synchronizedMap(new HashMap<String, Map<UUID, SubscriptionInfo>>());

    /**
     * Creates a new SubscriptionManager.
     */
    public SubscriptionManager() {
    }

    /**
     * Processes a UPnP SUBSCRIBE request and creates or renews a subscription.
     * 
     * @param key
     *            The key identifies the resource to which this subscription applies.
     * @param req
     *            Subscription request
     * @param res
     *            Response to the subscription request
     * @throws IOException
     *             Thrown by HttpServletResponse.sendError if an error occurs writing the response.
     */
    public void processSubscribe(String key, HttpServletRequest req, HttpServletResponse res) throws IOException {
        String timeoutHdr = req.getHeader("TIMEOUT");
        String callbackHdr = req.getHeader("CALLBACK");
        String sidHdr = req.getHeader("SID");
        List<URL> callbackUrls = new LinkedList<URL>();

        // Perform error checking:
        // 1. Method must be SUBSCRIBE
        // 2. If no SID header
        // a. CALLBACK header must be present & properly formatted as a correct URL
        // b. NT header must be present with a value of "upnp:event"
        // 3. If there is a SID header, CALLBACK and NT headers not present
        if (!"SUBSCRIBE".equalsIgnoreCase(req.getMethod())) {
            // Return 405 status
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "Method " + req.getMethod() + " not allowed for this resource.");
            return;
        }

        if (sidHdr != null && (timeoutHdr != null || callbackHdr != null)) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "An SID header field and one of NT or CALLBACK header fields are present.");
            return;
        } else {
            if (callbackHdr == null) {
                // CALLBACK is a required header. Return status 412
                res.sendError(HttpServletResponse.SC_PRECONDITION_FAILED, "CALLBACK header is missing.");
                return;
            } else {
                // parse callback header and ensure proper format
                callbackUrls = parseCallbackHeader(callbackHdr);
                if (callbackUrls.size() == 0) {
                    res.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                            "CALLBACK header doesn't contain a valid HTTP URL.");
                    return;
                }
            }

            if (!"upnp:event".equals(req.getHeader("NT"))) {
                // NT is a required header. Return status 412
                res.sendError(HttpServletResponse.SC_PRECONDITION_FAILED, "NT header field does not equal upnp:event.");
                return;
            }
        }

        // parse timeout header
        long timeout = DEFAULT_TIMEOUT;
        try {
            timeout = Long.parseLong(timeoutHdr.substring(7)) * 1000;
        } catch (NumberFormatException e) {
            // ignore, use default
            LOG.info("Using default timeout", e);
        }

        // check if new subscription or a renewal
        if (sidHdr != null) { // subscription renewal
            Map<UUID, SubscriptionInfo> m = subscriptions.get(key);
            if (m == null) {
                res.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                        "SID doesn't correspond to a known subscription.");
                return;
            }

            // parse SID
            String ss = sidHdr.substring(5).trim();
            UUID sid = new UUID(ss);

            SubscriptionInfo si = m.get(sid);
            if (si == null) {
                res.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                        "SID doesn't correspond to a known subscription.");
                return;
            }

            si.renew(timeout);
        } else { // new subscription

            // create subscription identifier
            UUID sid = new UUID();

            addSubscription(key, sid, new SubscriptionInfo(sid, timeout, callbackUrls));

            // Create response
            res.setStatus(HttpServletResponse.SC_OK);
            res.addHeader("SERVER", System.getProperty("os.name") + "/" + System.getProperty("os.version")
                    + ", UPnP/1.1, rpi-rest/0.1");
            res.addHeader("SID", "uuid:" + sid.toString());
            res.addHeader("TIMEOUT", "Second-" + (timeout / 1000));
        }
    }

    /**
     * Processes a UPnP UNSUBSCRIBE request and removes a subscription.
     * 
     * @param key
     *            The key identifies the resource to which the subscription applies.
     * @param req
     *            HTTP request
     * @param res
     *            Response to the request
     * @throws IOException
     *             Thrown by HttpServletResponse.sendError if an error occurs writing the response.
     */
    public void processUnsubscribe(String key, HttpServletRequest req, HttpServletResponse res) throws IOException {
        String timeoutHdr = req.getHeader("TIMEOUT");
        String callbackHdr = req.getHeader("CALLBACK");
        String sidHdr = req.getHeader("SID");

        // Perform error checking:
        // 1. Method must be UNSUBSCRIBE
        // 2. SID header must be present
        // 3. NT and CALLBACK headers not present
        if (!"UNSUBSCRIBE".equalsIgnoreCase(req.getMethod())) {
            // Return 405 status
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "Method " + req.getMethod() + " not allowed for this resource.");
            return;
        }

        if (sidHdr == null || sidHdr.length() == 0) {
            res.sendError(HttpServletResponse.SC_PRECONDITION_FAILED, "SID header field is missing or empty.");
        }

        if (timeoutHdr != null || callbackHdr != null) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "An SID header field and one of NT or CALLBACK header fields are present.");
            return;
        }

        Map<UUID, SubscriptionInfo> m = subscriptions.get(key);
        if (m == null) {
            res.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                    "SID doesn't correspond to a known subscription.");
            return;
        }

        // parse SID & remove subscription
        String ss = sidHdr.substring(5).trim();
        UUID sid = new UUID(ss);
        if (m.remove(sid) == null) {
            res.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                    "SID doesn't correspond to a known subscription.");
            return;
        }
    }

    /**
     * Returns the SubscriptionInfo for all subscribers to the specified key/resource.
     * 
     * @param key
     *            Identifies the resource for which subscribers are to be returned.
     * @return A collection of the subscription information.
     */
    public Collection<SubscriptionInfo> getSubscriptions(final String key) {
        if (subscriptions.get(key) != null) {
            return Collections.unmodifiableCollection(subscriptions.get(key).values());
        }
        return new ArrayList<SubscriptionInfo>();
    }

    /**
     * Parses the CALLBACK header which is a <> delimited list of URLs. Does no error checking beyond that of the URL
     * constructor, and will ignore malformed URLs.
     * 
     * @param header
     *            Header string value
     * @return List of <> delimited strings from the header
     */
    protected List<URL> parseCallbackHeader(String header) {
        Pattern p = Pattern.compile("[^<>]+");
        Matcher m = p.matcher(header);
        List<URL> callbackUrls = new LinkedList<URL>();
        while (m.find()) {
            try {
                callbackUrls.add(new URL(m.group()));
            } catch (MalformedURLException e) {
                LOG.warn("Ignoring malformed URL", e);
            }
        }
        return callbackUrls;
    }

    /**
     * Adds a subscription to the map, creating a new one if necessary for the provided key.
     * 
     * @param key
     *            Resource key
     * @param sid
     *            subscription ID for this subscription
     * @param si
     *            The SubscriptionInfo to be added.
     */
    private void addSubscription(final String key, final UUID sid, final SubscriptionInfo si) {
        Map<UUID, SubscriptionInfo> m = subscriptions.get(key);
        if (m == null) {
            m = new HashMap<UUID, SubscriptionInfo>();
            subscriptions.put(key, m);
        }
        m.put(sid, si);
    }

    /**
     * Sends a UPnP NOTIFY message to all subscribers of the resource identified by the key, using the provided content
     * type and body content.
     *
     * @param key
     *            The key identifying the resource for which the event applies.
     * @param contentType
     *            The content type of the message body.
     * @param body
     *            The content for the message body.
     */
    public void fireEvent(final String key, final String contentType, final String body) {
        Map<UUID, SubscriptionInfo> m = subscriptions.get(key);

        if (m == null) {
            return;
        }

        List<UUID> keys = new ArrayList<UUID>(m.keySet());
        for (UUID uuid : keys) {
            SubscriptionInfo si = m.get(uuid);

            // check that it isn't expired
            if (si.expiration > System.currentTimeMillis()) {
                // try sending to callback URLs until one is successful
                for (URL url : si.callbackUrls) {
                    try {
                        String message = generateNotify(url, contentType, body, uuid, si);
                        sendNotify(url, message);
                        si.incrementEventKey();
                        break;
                    } catch (IOException e) {
                        LOG.warn(e);
                    }
                }
            } else {
                // remove expired subscription
                m.remove(uuid);
            }
        }
    }

    /**
     * Sends the provided message to the host and port specified in the URL object.
     * 
     * @param url
     *            Provides the host and port to which the message is sent via TCP.
     * @param message
     *            The message to send, including all headers and message body.
     * @throws IOException
     *             If an exception occured writing to the socket.
     */
    private void sendNotify(final URL url, final String message) throws IOException {
        Socket sock = new Socket(url.getHost(), url.getPort());
        OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream());
        out.write(message);
        out.close();
        sock.close();
    }

    /**
     * Generates the UPnP NOTIFY message string.
     * 
     * @param url
     *            URL for the subscriber.
     * @param contentType
     *            Content type header.
     * @param body
     *            message body content
     * @param uuid
     *            The universally unique subscription ID.
     * @param si
     *            Subscription info used to complete the message
     * @return The String for the message that should be transmitted to the subscriber.
     */
    private String generateNotify(final URL url, String contentType, String body, UUID uuid, SubscriptionInfo si) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.printf("NOTIFY %s HTTP/1.1\r\n", url.getPath());
        pw.printf("HOST: %s:%d\r\n", url.getHost(), url.getPort());
        pw.printf("CONTENT-TYPE: %s\r\n", contentType);
        pw.printf("NT: upnp:event\r\n");
        pw.printf("NTS: upnp:propchange\r\n");
        pw.printf("SID: uuid:%s\r\n", uuid.toString());
        pw.printf("SEQ: %d\r\n", si.eventKey);
        pw.printf("CONTENT-LENGTH: %d\r\n", body.length());
        pw.printf("\r\n");
        pw.print(body);
        pw.flush();
        String resp = sw.toString();
        pw.close();
        return resp;
    }

    /**
     * Contains information about a single subscription.
     */
    public class SubscriptionInfo {
        private UUID sid;
        private long expiration;
        private List<URL> callbackUrls;
        private long eventKey = 0;

        private SubscriptionInfo(final UUID sid, final long timeout, final List<URL> callbacks) {
            this.sid = sid;
            renew(timeout);
            callbackUrls = callbacks;
        }

        void renew(final long timeout) {
            expiration = System.currentTimeMillis() + timeout;
        }

        void incrementEventKey() {
            if (eventKey == 4294967295L) {
                eventKey = 1;
            } else {
                eventKey++;
            }
        }

        /**
         * @return The subscription ID.
         */
        public UUID getSid() {
            return sid;
        }

        /**
         * @return The time the subscription expires, in ms from the time epoch.
         */
        public long getExpiration() {
            return expiration;
        }

        /**
         * @return The list of all callback URLs proved by the subscriber.
         */
        public List<URL> getCallbackUrls() {
            return Collections.unmodifiableList(callbackUrls);
        }

        /**
         * @return The event key integer value to be used for the next event sent.
         */
        public long getEventKey() {
            return eventKey;
        }
    }
}
