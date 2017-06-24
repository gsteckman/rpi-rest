/**
 *  RPi GPIO Pin
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
	definition (name: "RPi GPIO Pin", namespace: "gsteckman", author: "Greg Steckman") {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"
        capability "Refresh"
	}

	tiles{
		standardTile("pin", "device.switch", width: 1, height: 1, canChangeIcon: true, inactiveLabel: false) {
			state "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor: "#ffffff"
            state "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor: "#00a0dc"
		}
    }
}

// parse events into attributes
def parse(String description) {
	log.debug "RPi GPIO Pin Parsing '${description}'"
}

public void refresh(){
	parent.sendHubCommand(parent.get("/gpios/${device.deviceNetworkId}"));
}

// handle commands
def on() {
	log.debug "Executing 'on'"
    def stateString="HIGH"
    if(state.activeLow){
    	stateString="LOW"
    }
	parent.sendHubCommand(parent.post("/gpios/${device.deviceNetworkId}", [state: stateString]))
}

def off() {
	log.debug "Executing 'off'"
    def stateString = "LOW"
    if(state.activeLow){
    	stateString="HIGH"
    }
	parent.sendHubCommand(parent.post("/gpios/${device.deviceNetworkId}", [state: stateString]))
}

// Configures this pin to be an active low pin.
public setActiveLow(){
	state.activeLow=true;
}
