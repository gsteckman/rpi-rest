package io.github.gsteckman.rpi_rest;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioPinDigital;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiBcmPin;

/**
 * RestGpioController.java
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
@RestController
public class RestGpioController {
    private static final Log LOG = LogFactory.getLog(RestGpioController.class);
    private GpioController gpio;
    private boolean doorState = false;
    private GpioPinDigitalOutput pin4;
    private GpioPinDigitalOutput pin17;

    public RestGpioController(final GpioController gpioCtrl) {
        gpio = gpioCtrl;
        pin4 = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_04, PinState.LOW);
        pin4.setShutdownOptions(true, PinState.LOW);
        pin17 = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_17, PinState.LOW);
        pin17.setShutdownOptions(true, PinState.LOW);
    }

    /**
     * POST /door REST endpoint to open/close the door.
     * 
     * Body
     * 
     * {action: "open" | "close"}
     * 
     * Response:
     * 
     * { name: "door", state: "open" | "closed" }
     * 
     * @param model
     *            JSON data model provided in the body of the POST
     */
    @PostMapping(path = "/door")
    public Map<String, Object> door(@RequestBody Map<String, Object> model) {
        LOG.debug("POST door");
        String action = (String) model.get("action");
        if ("open".equals(action)) {
            synchronized (gpio) {
                pin4.setState(false);
                pin17.setState(false);
                pin4.pulse(100, true);
                doorState = true;
            }
        } else {
            synchronized (gpio) {
                pin4.setState(false);
                pin17.setState(false);
                pin17.pulse(100, true);
                doorState = false;
            }
        }
        return getDoorState();
    }

    /**
     * 
     * Response:
     * 
     * {name: "door", state: "open" | "closed"}
     * 
     * @return Map of name/value pairs that Spring converts to a HTTP JSON Response
     */
    @GetMapping(path = "/door")
    public Map<String, Object> getDoorState() {
        LOG.debug("GET door");

        Map<String, Object> returnMap = new HashMap<String, Object>();
        returnMap.put("name", "door");
        if (doorState) {
            returnMap.put("state", "open");
        } else {
            returnMap.put("state", "closed");
        }
        return returnMap;
    }

    /**
     * GPIOS POST handler. POST parameter named "state" should be provided in the POST body to set the value either "HIGH" or "LOW"
     * 
     * @param pinName
     *            Name of Pin to be set/cleared
     * @param state
     *            High=set, Low=clear
     * @return The GPIO state
     */
    @PostMapping(path = "/gpios/{address}")
    public Map<String, Object> setGpio(@PathVariable int address, @RequestBody Map<String, String> model) {
        LOG.debug("setGpio");
        GpioPin pin = gpio.getProvisionedPin(RaspiBcmPin.getPinByAddress(address));
        if (pin != null) {
            if (pin instanceof GpioPinDigitalOutput) {
                if (model.get("state") != null && model.get("state").toUpperCase().equals("HIGH")) {
                    ((GpioPinDigitalOutput) pin).setState(true);
                } else {
                    ((GpioPinDigitalOutput) pin).setState(false);
                }
            }
            if (pin instanceof GpioPinDigital)
                return mapFromPinState((GpioPinDigital) pin);
            else
                return mapFromPinState(pin);
        }
        return null;
    }

    @GetMapping(path = "/gpios/{address}")
    public Map<String, Object> getGpio(@PathVariable int address) {
        LOG.debug("getGpio");
        GpioPin pin = gpio.getProvisionedPin(RaspiBcmPin.getPinByAddress(address));
        if (pin != null) {
            if (pin instanceof GpioPinDigital)
                return mapFromPinState((GpioPinDigital) pin);
            else
                return mapFromPinState(pin);
        }
        return null;
    }

    @GetMapping(path = "/gpios")
    public List<Map<String, Object>> getGpios() {
        LOG.debug("getGpios");
        List<Map<String, Object>> l = new LinkedList<Map<String, Object>>();
        l.add(mapFromPinState(pin4));
        l.add(mapFromPinState(pin17));
        return l;
    }

    @PreDestroy
    public void shutdown() {
        gpio.shutdown();
    }

    private Map<String, Object> mapFromPinState(final GpioPin pin) {
        Map<String, Object> m = new HashMap<String, Object>();
        if (pin != null) {
            m.put("mode", pin.getMode().toString());
            m.put("address", RaspiBcmPin.getPinByName(pin.getName()).getAddress());
        }
        return m;
    }

    private Map<String, Object> mapFromPinState(final GpioPinDigital pin) {
        Map<String, Object> m = new HashMap<String, Object>();
        if (pin != null) {
            m.put("mode", pin.getMode().toString());
            m.put("address", RaspiBcmPin.getPinByName(pin.getName()).getAddress());
            m.put("state", pin.getState().toString());
        }
        return m;
    }
}
