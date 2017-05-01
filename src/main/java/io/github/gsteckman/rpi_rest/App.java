package io.github.gsteckman.rpi_rest;

/*
 * App.java
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

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioProvider;
import com.pi4j.io.gpio.RaspiGpioProvider;
import com.pi4j.io.gpio.RaspiPinNumberingScheme;
import com.pi4j.wiringpi.GpioUtil;

/**
 * This class serves as the application entry point and Spring Framework Boot configuration for the application. 
 * @author Greg Steckman
 *
 */
@SpringBootApplication
public class App {
    /**
     * Application entry point.
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        GpioUtil.enableNonPrivilegedAccess();
        SpringApplication app = new SpringApplication(App.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }

    public App() {

    }

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
     * @param gp The GpioProvider to be used by the controller.
     * @return The GpioController to be used by the application.
     */
    @Bean
    public GpioController gpioController(final GpioProvider gp) {
        GpioFactory.setDefaultProvider(gp);
        GpioController gpio = GpioFactory.getInstance();
        return gpio;
    }

    /**
     * Creates and returns the RestGpioController bean.
     * @param gc GpioController to be used by the RestGpioController.
     * @return A new RestGpioController.
     */
    @Bean
    public RestGpioController restGpioController(final GpioController gc) {
        return new RestGpioController(gc);
    }

}
