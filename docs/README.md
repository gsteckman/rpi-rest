# About
rpi-rest provides a REST interface for the [Raspberry Pi][1] GPIO with [SmartThings][2] integration. The REST interface is written in [Java][4] and leverages the [Spring Framework][3] and [The Pi4J Project][5]. A SmartApp and DeviceHandler provide the integration between the SmartThings app on your phone and the REST interface running on the Raspberry Pi. The REST interface can be used without SmartThings, but the SmartThings integration requires the REST interface. 

# Architecture
The system consists of software components running on the Raspberry Pi and within the SmartThings infrastructure as shown in the below figure.

![System Architecture][6]

On the Rasperry Pi, the REST Controller provides the REST interface for external components to read/write GPIO pins, and interfaces with the Raspberry Pi hardware via the Pi4J library. The SSDP Handler is responsible for implementing the [Simple Service Discovery Protocol][7] as defined by the [UPnP Device Architecture 1.1][8].

Within the SmartThings infrastructure, the rpi-service-manager is a SmartApp that discovers the Raspberry Pi device using SSDP and provides the user interface for selecting discovered devices. Upon user confirmation it adds the RPi REST Device Handler for interfacing with the REST Controller. The device handler produces the user interface for interacting with the GPIO and communicates with the REST Controller running on the Raspberry Pi to execute user commands.



[1]: https://www.raspberrypi.org/
[2]: https://www.smartthings.com/
[3]: https://spring.io/
[4]: https://www.oracle.com/java/index.html
[5]: http://pi4j.com/
[6]: architecture.svg "System Architecture"
[7]: https://en.wikipedia.org/wiki/Simple_Service_Discovery_Protocol
[8]: http://www.upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.1.pdf