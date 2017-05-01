/**
 *  RPi REST Device Handler
 *
 *  Copyright 2017 Greg Steckman
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "RPi REST Device Handler", namespace: "gsteckman", author: "Greg Steckman") {
		capability "Actuator"
		capability "Door Control"
        capability "Switch"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
	}

	tiles(scale: 2) {
		standardTile("door", "device.door", width: 3, height: 3, canChangeIcon: true, inactiveLabel: false, decoration: "flat") {
			state "open", label:'Open', action:"door control.close", icon:"st.doors.garage.garage-open", backgroundColor: "#00a0dc", nextState: "closed"
            state "closed", label:'Closed', action:"door control.open", icon:"st.doors.garage.garage-closed", backgroundColor: "#ffffff", nextState: "open"
		}
      
        childDeviceTiles("gpio")
        standardTile("refreshTile", "device.power", decoration: "ring") {
       	 	state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
    	}
        main("door")
        details(["door", "refreshTile", "gpio"])
	}
}

def installed(){
	log.debug "RPi REST Device Handler Installed"
    createChildDevices();
    refresh();
}

def updated() {
	log.debug "RPi REST Device Handler Updated"
	if (!childDevices) {
		createChildDevices()
	}
}

def initialize(){
	log.debug "RPi REST Device Handler Initialize"
}

// parse events into attributes
def parse(String description) {
	def msg = parseLanMessage(description)
    log.debug "Parsed message: " + msg
    def bodyText = msg.body
    def bodyMap = parseJson(bodyText)
    
    def result;
    if(bodyMap.name == "door"){
    	result = createEvent(name: "door", value: bodyMap.state)
    }else{
    	if(bodyMap.address == 4){
            def childDevice = childDevices.find{bodyMap.address}
        	childDevice.sendEvent(name: "switch", value: bodyMap.state=="HIGH"? "on" : "off")
        }
        if(bodyMap.address == 17){
    		def childDevice = childDevices.find{bodyMap.address}
        	childDevice.sendEvent(name: "switch", value: bodyMap.state=="HIGH"? "on" : "off")
    	}
    }

    log.debug "Parse returned ${result?.descriptionText}"

    return result
}

def on() {
	log.debug "Executing on"
    return post("/gpios/4", [state: "HIGH"])
}

def off() {
	log.debug "Executing off"
    return post("/gpios/4", [state: "LOW"])
}

def open() {
	log.debug "Executing 'open'"
    return post("/door", [action: "open"])
}

def close() {
	log.debug "Executing 'close'"
    return post("/door", [action: "close"])
}

def poll() {
	log.debug "Executing 'poll'"
	return get("/door")
}

def refresh() {
	log.debug "Executing 'refresh'"
    parent.sendHubCommand(get("/door"));
   
    childDevices.each{
    	it.refresh()
    }
}

def post(path, bodyMap){
	log.debug "POST"
	def address = convertHexToIP(getDataValue("ip"))+":"+convertHexToInt(getDataValue("port"))
	def result = new physicalgraph.device.HubAction(
    	method: "POST",
        path: path,
        HOST: address,
        headers: [
        	HOST: address
        ],
        body: bodyMap
 	)
    log.debug result
    return result;
}

def get(path){
	def address = convertHexToIP(getDataValue("ip"))+":"+convertHexToInt(getDataValue("port"))
	def result = new physicalgraph.device.HubAction(
    	method: "GET",
        path: path,
        HOST: address,
        headers: [
        	HOST: address
        ]
 	)
    return result;
}

private void createChildDevices() {
	log.debug "createChildDevices"
	state.oldLabel = device.label
	
    addChildDevice("RPi GPIO Pin", "4", null,
                   [completedSetup: true, label: "GPIO 4",
                    isComponent: true, componentName: "GPIO 4", componentLabel: "GPIO 4"])

    addChildDevice("RPi GPIO Pin", "17", null,
                   [completedSetup: true, label: "GPIO 17",
                    isComponent: true, componentName: "GPIO 17", componentLabel: "GPIO 17"])
}

def sync(ip, port) {
	def existingIp = getDataValue("ip")
	def existingPort = getDataValue("port")
	if (ip && ip != existingIp) {
		updateDataValue("ip", ip)
	}
	if (port && port != existingPort) {
		updateDataValue("port", port)
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}