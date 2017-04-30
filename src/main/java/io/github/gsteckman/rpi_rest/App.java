package io.github.gsteckman.rpi_rest;

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
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        GpioUtil.enableNonPrivilegedAccess();
        SpringApplication app = new SpringApplication(App.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }

    public App() {

    }

    @Bean
    public SsdpHandler ssdpHandler() {
        return SsdpHandler.getInstance();
    }

    @Bean
    public GpioProvider raspiGpioProvider() {
        return new RaspiGpioProvider(RaspiPinNumberingScheme.BROADCOM_PIN_NUMBERING);
    }

    @Bean
    public GpioController gpioController(final GpioProvider gp) {
        GpioFactory.setDefaultProvider(gp);
        GpioController gpio = GpioFactory.getInstance();
        return gpio;
    }

    @Bean
    public RestGpioController restGpioController(final GpioController gc) {
        return new RestGpioController(gc);
    }

}
