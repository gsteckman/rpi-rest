package io.github.gsteckman.rpi_rest;

/*
 * RestGpioController.java
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

import java.util.Collection;

/*
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
import com.pi4j.io.gpio.RaspiBcmPin;

/**
 * This class implements a REST interface to the Raspberry Pi GPIO. Methods that read/write the GPIO are synchronized on
 * the GpioController object so as to avoid concurrent access from multiple threads. Other users of the GpioController
 * instance should take care to prevent concurrent access with this class' instance by also synchronizing on the object
 * or with other suitable mechanisms.
 * 
 * @author Greg Steckman
 *
 */
@RestController
public class RestGpioController {
    private static final Log LOG = LogFactory.getLog(RestGpioController.class);
    private GpioController gpio;

    /**
     * Creates a new instance using the specified GpioController.
     * 
     * @param gpioCtrl
     *            GpioController to be used for controlling GPIO resources.
     */
    public RestGpioController(final GpioController gpioCtrl) {
        gpio = gpioCtrl;
    }

    /**
     * GPIOS POST handler. POST parameter named "state" should be provided in the POST body to set the value either
     * "HIGH" or "LOW"
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
        synchronized (gpio) {
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
        }
        return null;
    }

    /**
     * GET handler to return status of a gpio pin.
     * 
     * @param address
     *            Pin for which to return status.
     * @return Map of pin state attributes, which Spring Framework converts to a JSON formatted HTTP response.
     */
    @GetMapping(path = "/gpios/{address}")
    public Map<String, Object> getGpio(@PathVariable int address) {
        LOG.debug("getGpio");
        synchronized (gpio) {
            GpioPin pin = gpio.getProvisionedPin(RaspiBcmPin.getPinByAddress(address));
            if (pin != null) {
                if (pin instanceof GpioPinDigital)
                    return mapFromPinState((GpioPinDigital) pin);
                else
                    return mapFromPinState(pin);
            }
        }
        return null;
    }

    /**
     * Provides the status of all GPIO pins.
     * 
     * @return A list of Maps of pin state attributes, one per pin, which Spring Framework converts to a JSON formatted
     *         HTTP response.
     */
    @GetMapping(path = "/gpios")
    public List<Map<String, Object>> getGpios() {
        LOG.debug("getGpios");
        List<Map<String, Object>> l = new LinkedList<Map<String, Object>>();
        synchronized (gpio) {
            Collection<GpioPin> pins = gpio.getProvisionedPins();
            for (GpioPin pin : pins) {
                l.add(mapFromPinState(pin));
            }
        }
        return l;
    }

    /**
     * shuts down the GPIO.
     */
    @PreDestroy
    public void shutdown() {
        synchronized (gpio) {
            gpio.shutdown();
        }
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
