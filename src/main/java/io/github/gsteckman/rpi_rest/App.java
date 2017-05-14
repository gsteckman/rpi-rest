package io.github.gsteckman.rpi_rest;

/*
 * App.java
 * 
 * Copyright 2017 Greg Steckman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing permissions and limitations under the License.
 *
 */

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioProvider;
import com.pi4j.io.gpio.RaspiGpioProvider;
import com.pi4j.io.gpio.RaspiPinNumberingScheme;

/**
 * This class serves as a Spring Framework Boot configuration for the base rpi-rest interface. It should be subclassed
 * to implement the gpioController method and optionally a main method.
 * 
 * The gpioController method of the subclass should create and configure the GpioController object for the GPIO pins
 * needed by the application and their corresponding direction (input/output), as illustrated in the example below that
 * provisions and configures pins 4 and 17 for output:
 * 
 * <code>
 * &#64;Bean
 * &#64;Override
 * public GpioController gpioController(final GpioProvider gp) {
 *    GpioFactory.setDefaultProvider(gp);
 *    GpioController gpio = GpioFactory.getInstance();
 *    GpioPin pin4 = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_04, PinState.LOW);
 *    pin4.setShutdownOptions(true, PinState.LOW);
 *    GpioPin pin17 = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_17, PinState.LOW);
 *    pin17.setShutdownOptions(true, PinState.LOW);
 *    return gpio;
 * }
 * </code>
 * 
 * The subclass' optional main method is used to bootstrap the application, and if using the Spring Boot framework would
 * be similar to the below code:
 * 
 * <code>
 * public static void main(String[] args) {
 *    GpioUtil.enableNonPrivilegedAccess(); //See pi4j documentation for details
 *    SpringApplication app = new SpringApplication(AppSubclass.class); //Where "AppSubclass" is the name of the subclass
 *    app.run(args);
 * }
 * </code>
 * 
 * @author Greg Steckman
 *
 */
@SpringBootApplication
public abstract class App {
    /**
     * @return The SsdpHandler to be used by the application.
     */
    @Bean
    public SsdpHandler ssdpHandler() {
        return SsdpHandler.getInstance();
    }

    /**
     * @return The GpioProvider to be used by the GpioController.
     */
    @Bean
    public GpioProvider raspiGpioProvider() {
        return new RaspiGpioProvider(RaspiPinNumberingScheme.BROADCOM_PIN_NUMBERING);
    }

    /**
     * This method should be overridden by a subclass that creates a GpioController with the required pins provisioned
     * for input/output as needed by the end application.
     * 
     * @param gp
     *            The GpioProvider to be used by the controller.
     * @return The GpioController to be used by the application.
     */
    @Bean
    public abstract GpioController gpioController(final GpioProvider gp);

    /**
     * Creates and returns the RestGpioController bean.
     * 
     * @param gc
     *            GpioController to be used by the RestGpioController.
     * @return A new RestGpioController.
     */
    @Bean
    public RestGpioController restGpioController(final GpioController gc) {
        return new RestGpioController(gc);
    }

}
