/**
 *  GE Link Bulb
 *
 *  Copyright 2014 SmartThings
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
 *  Thanks to Chad Monroe @cmonroe and Patrick Stuart @pstuart, and others
 *
 ******************************************************************************
 *                                Changes
 ******************************************************************************
 *
 *  Change 1:	2014-10-10 (wackford)
 *				Added setLevel event so subscriptions to the event will work
 *  Change 2:	2014-12-10 (jscgs350 using Sticks18's code and effort!)
 *				Modified parse section to properly identify bulb status in the app when manually turned on by a physical switch
 *  Change 3:	2014-12-12 (jscgs350, Sticks18's)
 *				Modified to ensure dimming was smoother, and added fix for dimming below 7
 *	Change 4:	2014-12-14 Part 1 (Sticks18)
 *				Modified to ignore unnecessary level change responses to prevent level skips
 *	Change 5:	2014-12-14 Part 2 (Sticks18, jscgs350)
 *				Modified to clean up trace&debug logging, added new code from @sticks18 for parsing "on/off" to determine if the bulb is manually turned on and immediately update the app
 *	Change 6:	2015-01-02	(Sticks18)
 *				Modified to allow dim rate in Preferences. Added ability to dim during On/Off commands and included this option in Preferences. Defaults are "Normal" and no dim for On/Off.
 *	Change 7:	2015-01-09	(tslagle13)
 *				dimOnOff is was boolean, and switched to enum. Properly update "rampOn" and "rampOff" when refreshed or a polled (dim transition for On/Off commands)
 *
 *
 */
metadata {
	definition (name: "Orbit2", namespace: "test", author: "smartthings") {

        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "Polling"

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,0006,0201", outClusters: "0019"
	}

	// UI tile definitions
	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true, canChangeBackground: true) {
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff"
			state "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#79b821"
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main(["switch"])
		details(["switch", "refresh"])
	}
	
}

// Parse incoming device messages to generate events
def parse(String description) {
	log.trace description
    log.debug(zigbee.parse(description))
    //log.debug("Raw: $description")
    
    if (description?.startsWith("on/off:")) {    
		log.debug "The valve was sent a command to do something just now..."
		if (description[-1] == "1") {
        	def result = createEvent(name: "switch", value: "on")
            log.debug "On command was sent maybe from manually turning on? : Parse returned ${result?.descriptionText}"
            return result
        } else if (description[-1] == "0") {
        	def result = createEvent(name: "switch", value: "off")
            log.debug "Off command was sent : Parse returned ${result?.descriptionText}"
            return result
        }
    }
    
    def msg = zigbee.parse(description)

	if (description?.startsWith("catchall:")) {
		// log.trace msg
		// log.trace "data: $msg.data"

        def x = description[-4..-1]
        // log.debug x

        switch (x) 
        {

        	case "0000":

            	def result = createEvent(name: "switch", value: "off")
            	log.debug "${result?.descriptionText}"
           		return result
                break

            case "1000":

            	def result = createEvent(name: "switch", value: "off")
            	log.debug "${result?.descriptionText}"
           		return result
                break

            case "0100":

            	def result = createEvent(name: "switch", value: "on")
            	log.debug "${result?.descriptionText}"
           		return result
                break

            case "1001":

            	def result = createEvent(name: "switch", value: "on")
            	log.debug "${result?.descriptionText}"
           		return result
                break

        }
    }

    if (description?.startsWith("read attr")) {

        // log.trace description[27..28]
        // log.trace description[-2..-1]

    	if (description[27..28] == "0A") {

        	// log.debug description[-2..-1]
        	def i = Math.round(convertHexToInt(description[-2..-1]) / 256 * 100 )
			sendEvent( name: "level", value: i )
        	sendEvent( name: "switch.setLevel", value: i) //added to help subscribers

    	} 

    }

}

def poll() {

    [
	"st rattr 0x${device.deviceNetworkId} 1 6 0", "delay 500",
    "st rattr 0x${device.deviceNetworkId} 1 8 0", "delay 500",
    ]
    
}

def on() {
	state.lvl = "00"
    state.trigger = "on/off"

    // log.debug "on()"
	sendEvent(name: "switch", value: "on")
	"st cmd 0x${device.deviceNetworkId} 1 6 1 {}"
}

def off() {
	state.lvl = "00"
    state.trigger = "on/off"

    // log.debug "off()"
	sendEvent(name: "switch", value: "off")
	"st cmd 0x${device.deviceNetworkId} 1 6 0 {}"
}

def refresh() {

    [
	"st rattr 0x${device.deviceNetworkId} 1 6 0", "delay 500",
    "st rattr 0x${device.deviceNetworkId} 1 8 0", "delay 500",
    ]
    poll()
    
}

def setLevel(value) {

    def cmds = []

	if (value == 0) {
		sendEvent(name: "switch", value: "off")
		cmds << "st cmd 0x${device.deviceNetworkId} 1 8 0 {0000 ${state.rate}}"
	}
	else if (device.latestValue("switch") == "off") {
		sendEvent(name: "switch", value: "on")
	}

    sendEvent(name: "level", value: value)
    value = (value * 255 / 100)
    def level = hex(value);

    state.trigger = "setLevel"
    state.lvl = "${level}"

    if (dimRate) {
    	cmds << "st cmd 0x${device.deviceNetworkId} 1 8 4 {${level} ${state.rate}}"
    }
    else {   
    	cmds << "st cmd 0x${device.deviceNetworkId} 1 8 4 {${level} 1500}"
    }

    log.debug cmds
    cmds
}

def configure() {

	String zigbeeId = swapEndianHex(device.hub.zigbeeId)
	log.debug "Confuguring Reporting and Bindings."
	def configCmds = [	

        //Switch Reporting
        "zcl global send-me-a-report 6 0 0x10 0 3600 {01}", "delay 500",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1000",

	]
    return configCmds + refresh() // send refresh cmds as part of config
}

private hex(value, width=2) {
	def s = new BigInteger(Math.round(value).toString()).toString(16)
	while (s.size() < width) {
		s = "0" + s
	}
	s
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
    int i = 0;
    int j = array.length - 1;
    byte tmp;
    while (j > i) {
        tmp = array[j];
        array[j] = array[i];
        array[i] = tmp;
        j--;
        i++;
    }
    return array
}
