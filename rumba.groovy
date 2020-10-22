/**
*  Rumba v1.3
*  for 900/i7/s9 series
*  
*  Version History:
*.  1.3 Removed unecessary sendEvents from poll() function
*.  1.2 Attempted fix for polling interval and health check, added method to get robot IP address
*   1.1: Implemented health check capability
*   1.0: Initial release
*
*  Copyright 2020 Matvei Vevitsis
*  Based on iRobot Roomba v2.2 by Steve-Gregory (Copyright 2016)
*  with additional modifications by Adrian Caramaliu and Justin Dybedahl
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

/*Known NotReady states*/
def getRoombaStates() {
    def ROOMBA_READY = 0
    def ROOMBA_STUCK = 1
    def ROOMBA_BIN_FULL = 16
    def ROOMBA_DEBRIS_EXTRACTOR = 6
    def ROOMBA_NOT_UPRIGHT = 7
    def ROOMBA_IN_THE_DARK = 8
    def ROOMBA_BATTERYLOW = 15
    def ROOMBA_STATES = ['ready': ROOMBA_READY, 'stuck': ROOMBA_STUCK, 'full': ROOMBA_BIN_FULL, 'tilted': ROOMBA_NOT_UPRIGHT, 'dark': ROOMBA_IN_THE_DARK, 'batterylow': ROOMBA_BATTERYLOW, 'debrisextractors': ROOMBA_DEBRIS_EXTRACTOR]
    return ROOMBA_STATES
}
metadata {
    definition (name: "Rumba", namespace: "mvevitsis", author: "Matvei Vevitsis", ocfDeviceType: "oic.d.robotcleaner") {
        capability "robotCleanerMovement"
        //capability "robotCleanerCleaningMode"
        //capability "robotCleanerTurboMode"
        capability "Battery"
        capability "Switch"
        capability "Refresh"
        capability "Polling"
        capability "Consumable"
        capability "Timed Session"
        capability "Configuration"
        capability "Health Check"

        command "dock"
        command "resume"
        command "pause"
        command "cancel"
        command "pauseAndDock"

        attribute "totalJobs", "number"
        attribute "totalJobHrs", "number"
        attribute "headline", "string"
        attribute "robotName", "string"
        attribute "robotIpAddress", "string"
        attribute "preferences_set", "string"
        attribute "status", "string"
        //For ETA heuristic
        attribute "lastSqft", "number"
        attribute "lastRuntime", "number"
        attribute "lastDate", "string"
    }
}
// simulator metadata
simulator {
}
//Preferences
preferences {
    section("Cloud Roomba API Type") {
        input "localAPI", "bool", title: "Use a local REST gateway for Roomba", description: "Enable this if you have installed a local REST gateway for Roomba, you will need to provide the IP of that gateway", displayDuringSetup: true
    }
    section("Roomba Local Settings") {
    	input type: "paragraph", title: "Fill these parameters if using a local REST gateway"
        input "roomba_host", "string", title:"IP of Roomba local REST Gateway", displayDuringSetup: true
        input "roomba_port", "number", range: "1..65535", defaultValue: 3000, title:"Port of Roomba local REST Gateway", displayDuringSetup: true
    }
    section("Roomba Cloud Credentials") {
        input type: "paragraph", title: "Please fill in the Roomba credentials below if using a Cloud connection to your robot", description: "The username/password can be retrieved via node.js & dorita980", displayDuringSetup: true
        input "roomba_username", "text", title: "Roomba username/blid", displayDuringSetup: true
        input "roomba_password", "password", title: "Roomba password", displayDuringSetup: true
    }
    section("Misc.") {
       	//input "sendPushMessage", "enum", title: "Push Notifications", description: "Alert if Roomba encounters a problem", options: ["Yes", "No"], defaultValue: "No", required: true
        //input "sendAudioMessage", "enum", title: "Audio Notifications", options: ["Yes", "No"], defaultValue: "No", required: true
        //input "audioDevices", "capability.audioNotification", title: "Select a speaker", required: false, multiple: true
        input type: "paragraph", title: "Polling Interval [minutes]", description: "This feature allows you to change the frequency of polling for the robot in minutes (1-59)"
        input "pollInterval", "number", title: "Polling Interval", description: "Change polling frequency (in minutes)", defaultValue:4, range: "1..59", required: true, displayDuringSetup: true
    }
}
// UI tile definitions
tiles {
    multiAttributeTile(name:"CLEAN", type:"generic", width: 6, height: 4, canChangeIcon: true) {
        tileAttribute("device.status", key: "PRIMARY_CONTROL") {
            attributeState "error", label: 'Error', icon: "st.switches.switch.off", backgroundColor: "#bc2323"
            attributeState "bin-full", label: 'Bin Full', icon: "st.switches.switch.off", backgroundColor: "#bc2323"
            attributeState "docked", label: 'Start Clean', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "starting"
            attributeState "docking", label: 'Docking', icon: "st.switches.switch.off", backgroundColor: "#ffa81e"
            attributeState "starting", label: 'Starting Clean', icon: "st.switches.switch.off", backgroundColor: "#ffffff"
            attributeState "cleaning", label: 'Stop Clean', action: "stop", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: "pausing"
            attributeState "pausing", label: 'Stop Clean', icon: "st.switches.switch.on", backgroundColor: "#79b821"
            attributeState "paused", label: 'Send Home', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: "docking"
            attributeState "resuming", label: 'Stop Clean', icon: "st.switches.switch.on", backgroundColor: "#79b821"
        }
        tileAttribute("device.headline", key: "SECONDARY_CONTROL") {
           attributeState "default", label:'${currentValue}'
        }
    }
    valueTile("DOCK", "device.status", width: 2, height: 2) {
        state "docked", label: 'Docked', backgroundColor: "#79b821"
        state "docking", label: 'Docking', backgroundColor: "#ffa81e"
        state "starting", label: 'UnDocking', backgroundColor: "#ffa81e"
        state "cleaning", label: 'Not on Dock', backgroundColor: "#ffffff", nextState: "docking", action: "dock"
        state "pausing", label: 'Not on Dock', backgroundColor: "#ffffff", nextState: "docking", action: "dock"
        state "paused", label: 'Dock', backgroundColor: "#ffffff", nextState: "docking", action: "dock"
        state "bin-full", label: 'Bin full', backgroundColor: "#bc2323", nextState: "docking", action: "dock"
        state "resuming", label: 'Not on Dock', backgroundColor: "#ffffff", defaultState: true, nextState: "docking", action: "dock"
    }
    valueTile("PAUSE", "device.status", width: 2, height: 2) {
        state "docked", label: 'Pause', backgroundColor: "#ffffff", defaultState: true
        state "docking", label: 'Pause', backgroundColor: "#ffffff"
        state "starting", label: 'Pause', backgroundColor: "#ffffff", nextState: "pausing", action: "pause"
        state "cleaning", label: 'Pause', backgroundColor: "#ffffff", nextState: "pausing", action: "pause"
        state "pausing", label: 'Pausing..', backgroundColor: "#79b821"
        state "paused", label: 'Paused', backgroundColor: "#79b821"
        state "bin-full", label: 'Bin full', backgroundColor: "#bc2323"
        state "resuming", label: 'Pause', backgroundColor: "#ffffff", nextState: "pausing", action: "pause"
    }
    valueTile("RESUME", "device.status", width: 2, height: 2) {
        state "docked", label: 'Resume', backgroundColor: "#ffffff", defaultState: true
        state "docking", label: 'Resume', backgroundColor: "#ffffff"
        state "starting", label: 'Resume', backgroundColor: "#ffffff"
        state "cleaning", label: 'Resume', backgroundColor: "#ffffff"
        state "pausing", label: 'Resume', backgroundColor: "#79b821", nextState: "resuming", action: "switch.on"
        state "paused", label: 'Resume', backgroundColor: "#ffffff", nextState: "resuming", action: "switch.on"
        state "bin-full", label: 'Bin full', backgroundColor: "#bc2323"
        state "resuming", label: 'Resuming..', backgroundColor: "#79b821"
    }
    standardTile("refresh", "device.status", width: 4, height: 2, decoration: "flat") {
        state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
    }
    valueTile("battery", "device.battery", width: 2, height: 2, decoration: "flat") {
        state "default", label:'Battery ${currentValue}%'
    }

	valueTile("job_count", "device.totalJobs", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Number of Cleaning Jobs:\n${currentValue} jobs'
    }
    valueTile("job_hr_count", "device.totalJobHrs", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Total Job Time:\n${currentValue} hours'
    }
    valueTile("current_job_time", "device.runtimeMins", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Current Job Runtime:\n${currentValue} minutes'
    }
    valueTile("current_job_sqft", "device.runtimeSqft", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Current Job Sqft:\n${currentValue} ft'
    }
    valueTile("current_job_time_estimated", "device.timeRemaining", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Estimated Completion Time:\n${currentValue} minutes'
    }
    valueTile("current_job_sqft_estimated", "device.sqftRemaining", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Estimated Sqft Remaining:\n${currentValue} ft'
    }
    main "CLEAN"
    details(["STATUS",
             "CLEAN", "DOCK", "PAUSE", "RESUME",
             "refresh",
             "battery",
             "current_job_time", "current_job_time_estimated",
             "current_job_sqft", "current_job_sqft_estimated",
             "job_hr_count", "job_count"
             ])
}


//Settings updated
def updated() {
    //log.debug "Updated settings ${settings}..
    def interval 
    if (settings.pollInterval){
    	interval = settings.pollInterval
    } else {
    	interval = 4
    }
    runIn(3, "updateDeviceNetworkID")
    schedule("0 0/${interval} * * * ?", poll)  // 4min polling is normal for irobots
    sendEvent(name: "DeviceWatch-Enroll", value: [protocol: "cloud", scheme:"untracked"].encodeAsJson(), displayed: false)
    sendEvent(name: 'checkInterval', value: interval * 60 * 2, displayed: false, data: [ protocol: 'cloud', hubHardwareId: device.hub.hardwareID ] )
    //TODO Initialize tryCount
    //state.tryCount = 0
    //poll()
}

//Installed
def installed() {
	initialize()
}

//Configuration
def configure() {
   log.debug "Configuring.."
   poll()
}

//Initialize capabilities for new app UI display
def initialize() {
sendEvent(name: 'switch', value: 'off')
sendEvent(name: 'robotCleanerMovement', value: 'idle')
//sendEvent(name: 'robotCleanerCleaningMode', value: 'auto') 
//sendEvent(name: 'robotCleanerTurboMode', value: 'off')
}

//Timed Session
def setTimeRemaining(timeNumber) {
    log.debug "User requested setting the Time remaining to ${timeNumber}"
    return
}
//Consumable
def setConsumableStatus(statusString) {
    log.debug "User requested setting the Consumable Status - ${statusString}"
    def status = device.latestValue("status")
    log.debug "Setting value based on last roomba state - ${status}"

    if(roomba_value == "bin-full") {
        // Optionally this could be 'replace'?
        state.consumable = "maintenance_required"
    } else if(roomba_value == "error"){
        state.consumable = "missing"
    } else {
        state.consumable = "good"
    }
    return state.consumable
}
//Refresh
def refresh() {
    log.debug "Executing 'refresh'"
    //return poll
    return poll()
}

//TODO debug
//Ping
def ping() {
	log.debug "Device not responding, attempting to refresh..."
	return refresh
}


//TODO Check Roomba connection status
//def checkConnection(){
	//log.debug "Checking connection status..."
    //state.tryCount = state.tryCount + 1
    //log.debug "state.tryCount: ${state.tryCount}"
    //if (state.tryCount > 3) {
    	//log.debug "Connection is offline"
        //Display offline in UI
    	//sendEvent(name: 'healthStatus', value: 'offline' )
	//}

  	//def command = getPingCommand()
    //sendHubCommand(command)
//}
//TODO Parse results of connection check
//def parseCheckConnection(description) {
    //log.debug "Parsing connection status results"
    
    //def msg = parseLanMessage(description)
    //log.debug "Connection status: ${msg.status}"
    
    //if (msg.status == 200) {
        //state.tryCount = 0
        //log.debug "Connection is online"
        //sendEvent(name: 'healthStatus', value: 'online' )
	//}
//}

//TODO Setup the ping command 
//def getPingCommand() {
    //def result = new physicalgraph.device.HubAction(
        //method: "GET",
        //path: "/",
        //headers: [
            //HOST: getRobotAddress()
        //]
    //)
    
    //return result
//}

//Polling
def pollHistory() {
    log.debug "Polling for missionHistory ----"
    //sendEvent(name: "headline", value: "Polling history API", displayed: false)
    state.RoombaCmd = "missionHistory"
    return localAPI ? null : apiGet()
}
def poll() {
	//TODO Check robot connection status at each polling interval
    //checkConnection()
    //Get historical data first
    pollHistory()
    //Then poll for current status
    log.debug "Polling for status ----"
    //sendEvent(name: "headline", value: "Polling status API", displayed: false)
    state.RoombaCmd = "getStatus"
	return localAPI ? local_poll() : apiGet()
}

def sendMsg(message){
	def msg = message
    //Non functioning, removed from preferences
    if(sendPushMessage == "Yes") {
     	sendPush(msg)
  	}
    //Non functioning, removed from prefernces
    if(sendAudioMessage == "Yes"){
     	if(audioDevices){
  		audioDevices?.each { audioDevice -> 
       	if (audioDevice.hasCommand("playText")) { //Check if speaker supports TTS 
             audioDevice.playText(msg)
        } else {
        if (audioDevice.hasCommand("speak")) { //Check if speaker supports speech synthesis  
       		 audioDevice.speak(msg.toString())
        } else {
             audioDevice.playTrack(textToSpeech(msg)?.uri) //All other speakers
        }
        } 
  
        }
        }
    }
    
}




//robotCleanerCleaningMode methods
def setRobotCleanerCleaningMode(mode){
	if(mode == 'auto'){
    //For debug only
    sendEvent(name: 'robotCleanerCleaningMode', value: 'auto')
    //TODO Set cleaningPasses auto
    }
    if(mode == 'part'){
    //For debug only
    sendEvent(name: 'robotCleanerCleaningMode', value: 'part')
    }
    if(mode == 'repeat'){
    //For debug only
    sendEvent(name: 'robotCleanerCleaningMode', value: 'repeat')
    //TODO Set cleaningPasses two
    }
    if(mode == 'manual'){
    //For debug only
    sendEvent(name: 'robotCleanerCleaningMode', value: 'manual')
    //TODO Set cleaningPasses one
    }
    if(mode == 'stop'){
    //For debug only
    sendEvent(name: 'robotCleanerCleaningMode', value: 'stop')
    }
}
                         
//robotCleanerTurboMode methods
def setRobotCleanerTurboMode(mode){
	if(mode == 'on'){
    //For debug only
    sendEvent(name: 'robotCleanerTurboMode', value: 'on')
    //TODO Set CarpetBoost 'Performance'
    }
    if(mode == 'off'){
    //For debug only
    sendEvent(name: 'robotCleanerTurboMode', value: 'off')
    //TODO Set CarpetBoost 'Auto'
    }
    if(mode == 'silence'){
    //For debug only
    sendEvent(name: 'robotCleanerTurboMode', value: 'silence')
    //TODO Set CarpetBoost 'Eco'
    }
}
    
//robotCleanerMovement methods
def setRobotCleanerMovement(mode){
	def status = device.latestValue("status")
	if(mode == 'homing'){
    	//For debug only
    	sendEvent(name: 'robotCleanerMovement', value: 'homing')
    }
	if(mode == 'idle'){
    	//For debug only
    	sendEvent(name: 'robotCleanerMovement', value: 'idle')
        return pause()
    }
    if(mode == 'charging'){
    	//For debug only
    	sendEvent(name: 'robotCleanerMovement', value: 'charging' )
        return off()
    }
    if(mode == 'alarm'){
    	//For debug only
    	sendEvent(name: 'robotCleanerMovement', value: 'alarm' )
    }
    if(mode == 'powerOff'){
    	//For debug only
    	sendEvent(name: 'robotCleanerMovement', value: 'powerOff' )
    }
    if(mode == 'reserve'){
    	//For debug only
    	sendEvent(name: 'robotCleanerMovement', value: 'reserve')
    }
    if(mode == 'point'){
    	//For debug only
    	sendEvent(name: 'robotCleanerMovement', value: 'point')
    }
	if(mode == 'after'){
    	//For debug only
        sendEvent(name: 'robotCleanerMovement', value: 'after')
    }
    if(mode == 'cleaning'){
    	//For debug only
        sendEvent(name: 'robotCleanerMovement', value: 'cleaning')
        return on()
    }
}

// Switch methods
def on() {
    // Always start roomba
    def status = device.latestValue("status")
    log.debug "On based on state - ${status}"
    //For debug only
    sendEvent(name: 'switch', value: 'on') 
    //if(status == "paused")
	if(status == "pausing") {
	    return resume()
    } else {
	    return start()
	}
}
def off() {
    // Always return to dock..
	def status = device.latestValue("status")
    log.debug "Off based on state - ${status}"
    //For debug only
    sendEvent(name: 'switch', value: 'off') 
	if(status == "paused") {
    	return dock()
    } else {
	    return pauseAndDock()
    }
}
// Timed Session
def start() {
    sendEvent(name: "status", value: "starting")
    state.RoombaCmd = "start"
    runIn(15, poll)
	return localAPI ? local_start() : apiGet()
}
def stop() {
    sendEvent(name: "status", value: "stopping")
    state.RoombaCmd = "stop"
    runIn(15, poll)
    return localAPI ? local_stop() : apiGet()
}
def pauseAndDock() {
    sendEvent(name: "status", value: "pausing")
	state.RoombaCmd = "pause"
    return localAPI ? local_pauseAndDock() : apiGet()
}
def pause() {
    sendEvent(name: "status", value: "pausing")
    state.RoombaCmd = "pause"
    runIn(15, poll)
    return localAPI ? local_pause() : apiGet()
}
def cancel() {
	return off()
}

// Actions
def dock() {
    sendEvent(name: "status", value: "docking")
	state.RoombaCmd = "dock"
    runIn(15, poll)
	return localAPI ? local_dock() : apiGet()
}
def resume() {
    sendEvent(name: "status", value: "resuming")
    state.RoombaCmd = "resume"
    runIn(15, poll)
    return localAPI ? local_resume() : apiGet()
}
// API methods
def parse(description) {
	log.trace "GOT HERE"
    def msg = parseLanMessage(description)
    log.trace "GOT MSG $msg"
    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)
}

def apiGet() {
	log.debug "apiget"
	if (local) return
    def request_query = ""
    def request_host = ""
    def encoded_str = "${roomba_username}:${roomba_password}".bytes.encodeBase64()

    //Handle prefrences
    if("${roomba_host}" == "" || "${roomba_host}" == "null") {
        request_host = "https://irobot.axeda.com"
    } else {
        log.debug "Using Roomba Host: ${roomba_host}"
        request_host = "${roomba_host}"
    }

    //Validation before calling the API
    if(!roomba_username || !roomba_password) {
        def new_status = "Username/Password not set. Configure required before using device."
        sendEvent(name: "headline", value: new_status, displayed: false)
        sendEvent(name: "preferences_set", value: "missing", displayed: false)
        return
    } else if(state.preferences_set != "missing") {
        sendEvent(name: "preferences_set", value: "ready", displayed: false)
    }

    state.AssetID = "ElPaso@irobot!${roomba_username}"
    state.Authorization = "${encoded_str}"

    // Path (No changes required)
    def request_path = "/services/v1/rest/Scripto/execute/AspenApiRequest"
    // Query manipulation
    if( state.RoombaCmd == "getStatus" || state.RoombaCmd == "accumulatedHistorical" || state.RoombaCmd == "missionHistory") {
        request_query = "?blid=${roomba_username}&robotpwd=${roomba_password}&method=${state.RoombaCmd}"
    } else {
        request_query = "?blid=${roomba_username}&robotpwd=${roomba_password}&method=multipleFieldSet&value=%7B%0A%20%20%22remoteCommand%22%20:%20%22${state.RoombaCmd}%22%0A%7D"
    }

    def requestURI = "${request_host}${request_path}${request_query}"
    def httpRequest = [
        method:"GET",
        uri: "${requestURI}",
        headers: [
            'User-Agent': 'aspen%20production/2618 CFNetwork/758.3.15 Darwin/15.4.0',
            Accept: '*/*',
            'Accept-Language': 'en-us',
            'ASSET-ID': state.AssetID,
        ]
    ]
    try {
        httpGet(httpRequest) { resp ->
            log.debug "response Headers:" + resp.headers.collect { "${it.name}:${it.value}" }
            log.debug "response contentType: ${resp.contentType}"
            log.debug "response data: ${resp.data}"
            parseResponseByCmd(resp, state.RoombaCmd)
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
}

def parseResponseByCmd(resp, command) {
    def data = resp.data
    if(command == "getStatus") {
        setStatus(data)
    } else if(command == "accumulatedHistorical" ) {
        /*readSummaryInfo -- same as getStatus but easier to parse*/
    } else if(command == "missionHistory") {
        setMissionHistory(data)
    }
}
def convertDate(dateStr) {
    return Date.parse("yyyyMMdd H:m", dateStr)
}
def setMissionHistory(data) {
    def lastRuntime = -1
    def lastSqft = -1
    def lastDate = ""
    def mstatus = data.status
    def robot_history = data.missions

    robot_history.sort{ convertDate(it.date) }.each{ mission ->
        if(mission.done == 'ok') {
            lastSqft = mission.sqft
            lastRuntime = mission.runM
            lastDate = mission.date
        }
    }

    state.lastRuntime = lastRuntime
    state.lastSqft = lastSqft
    state.lastDate = lastDate

    sendEvent(name: "lastRuntime", value: state.lastRuntime, displayed: false)
    sendEvent(name: "lastSqft", value: state.lastSqft, displayed: false)
    sendEvent(name: "lastDate", value: state.lastDate, displayed: false)
}

def setStatus(data) {
    def rstatus = data.robot_status
    def robotName = data.robotName
	state.robotName = robotName

	def mission = data.mission
    def runstats = data.bbrun
    def cschedule = data.cleanSchedule
    def pmaint = data.preventativeMaintenance
    def robot_status = new groovy.json.JsonSlurper().parseText(rstatus)
    def robot_history = new groovy.json.JsonSlurper().parseText(mission)
    def runtime_stats = new groovy.json.JsonSlurper().parseText(runstats)
    def schedule = new groovy.json.JsonSlurper().parseText(cschedule)
    def maintenance = new groovy.json.JsonSlurper().parseText(pmaint)
    log.debug "Robot status = ${robot_status}"
    log.debug "Robot history = ${robot_history}"
    log.debug "Runtime stats= ${runtime_stats}"
    log.debug "Robot schedule= ${schedule}"
    log.debug "Robot maintenance= ${maintenance}"
    def current_cycle = robot_status['cycle']
    def current_charge = robot_status['batPct']
    def current_phase = robot_status['phase']
    def current_sqft = robot_status['sqft']
    def num_mins_running = robot_status['mssnM']
    def flags = robot_status['flags']  // Unknown what 'Flags' 0/1/2/5 mean?
    def readyCode = robot_status['notReady']
    def num_cleaning_jobs = robot_history['nMssn']
    def num_dirt_detected = runtime_stats['nScrubs']
    def total_job_time = runtime_stats['hr']
    

    def new_status = get_robot_status(current_phase, current_cycle, current_charge, readyCode)
    def roomba_value = get_robot_enum(current_phase, readyCode)

    log.debug("Robot updates -- ${roomba_value} + ${new_status}")
    //Set robotCleanerMovement state
    if(roomba_value == "cleaning"){
    	state.robotCleanerMovement = "cleaning"
    } else if(roomba_value == "paused"){
    	state.robotCleanerMovement = "idle"
    } else if (roomba_value == "docked"){
       state.robotCleanerMovement = "charging"
    } else if (roomba_value == "docking"){
	   state.robotCleanerMovement = "homing"
    } else if (roomba_value == "error"){
       state.robotCleanerMovement = "alarm"
    } else if (roomba_value == "bin-full"){
       state.robotCleanerMovement = "alarm"
    }
    
	//Set the state object
    if(roomba_value == "cleaning") {
        state.switch = "on"
    } else {
        state.switch = "off"
    }

    /* Consumable state-changes */
    if(roomba_value == "bin-full") {
        state.consumable = "maintenance_required"
    } else if(roomba_value == "error"){
        state.consumable = "missing"
    } else {
        state.consumable = "good"
    }

    /* Timed Session state-changes */
    if(roomba_value == "cleaning") {
        state.sessionStatus = "running"
    } else if (roomba_value == "paused") {
        state.sessionStatus = "paused"
    } else if (roomba_value == "docked" || roomba_value == "docking") {
        state.sessionStatus = "canceled"
    } else {
        state.sessionStatus = "stopped"
    }

    /* Misc. state-changes */
    if(state.lastRuntime == -1) {
        state.timeRemaining = -1
    } else {
        state.timeRemaining = state.lastRuntime - num_mins_running
    }
    if(state.lastSqft == -1) {
        state.sqftRemaining = -1
    } else {
        state.sqftRemaining = state.lastSqft - current_sqft
    }

    /*send events, display final event*/
    sendEvent(name: "robotName", value: robotName, displayed: false)
    sendEvent(name: "runtimeMins", value: num_mins_running, displayed: false)
    sendEvent(name: "runtimeSqft", value: current_sqft, displayed: false)
    sendEvent(name: "timeRemaining", value: state.timeRemaining, displayed: false)
    sendEvent(name: "sqftRemaining", value: state.sqftRemaining, displayed: false)
    sendEvent(name: "totalJobHrs", value: total_job_time, displayed: false)
    sendEvent(name: "totalJobs", value: num_cleaning_jobs, displayed: false)
    sendEvent(name: "battery", value: current_charge, displayed: false)
    sendEvent(name: "headline", value: new_status, displayed: false)
    sendEvent(name: "status", value: roomba_value)
    sendEvent(name: "switch", value: state.switch)
    sendEvent(name: "sessionStatus", value: state.sessionStatus)
    sendEvent(name: "consumable", value: state.consumable)
    sendEvent(name: 'robotCleanerMovement', value: state.robotCleanerMovement)


    
}

def get_robot_enum(current_phase, readyCode) {
    def ROOMBA_STATES = getRoombaStates()

    if(readyCode != ROOMBA_STATES['ready']) {
        if(readyCode == ROOMBA_STATES['full']) {
            return "bin-full"
        } else if(readyCode != ROOMBA_STATES['dark']) {
            return "error"
        }
    }

    if(current_phase == "charge") {
        return "docked"
    } else if(current_phase == "hmUsrDock") {
        return "docking"
    } else if(current_phase == "pause" || current_phase == "stop") {
        return "paused"
    } else if(current_phase == "run") {
        return "cleaning"
    } else {
        //"Stuck" phase falls into this category.
        log.error "Unknown phase - Raw 'robot_status': ${status}. Add to 'get_robot_enum'"
        return "error"
    }
}
def parse_not_ready_status(readyCode) {
    def robotName = state.robotName
    def ROOMBA_STATES = getRoombaStates()

    if(readyCode == ROOMBA_STATES['full']) {
      return "${robotName}'s bin is full. Empty bin to continue."
      sendMsg("${robotName}'s bin is full. Empty bin to continue.")
    } else if(readyCode == ROOMBA_STATES['tilted']) {
      return "${robotName} is not upright. Place robot on flat surface to continue."
      sendMsg("${robotName} is not upright. Place robot on flat surface to continue.")
    } else if (readyCode == ROOMBA_STATES['stuck']) {
      return "${robotName} is stuck. Move robot to continue."
      sendMsg("${robotName} is stuck. Move robot to continue.")
    } else if (readyCode == ROOMBA_STATES['batterylow']) {
      return "${robotName}'s battery is low. Please send Roomba to dock or place on dock."
      sendMsg("${robotName}'s battery is low. Please send Roomba to dock or place on dock.")
    } else if (readyCode == ROOMBA_STATES['debrisextractors']) {
      return "${robotName}'s debris extractors are blocked. Please clear them."
      sendMsg("${robotName}'s debris extractors are blocked. Please clear them.")
    } else {
      return "${robotName} returned notReady=${readyCode}. See iRobot app for details."
      sendMsg("${robotName} returned notReady=${readyCode}. See iRobot app for details.")
    }
}

def get_robot_status(current_phase, current_cycle, current_charge, readyCode) {
    def robotName = state.robotName
    def ROOMBA_STATES = getRoombaStates()

    // 0 and 8 are "okay to run"
    if(readyCode != ROOMBA_STATES['ready'] && readyCode != ROOMBA_STATES['dark']) {
      return parse_not_ready_status(readyCode)
    } else if(current_phase == "charge") {
        if (current_charge == 100) {
            return "${robotName} is Docked/Fully Charged"
        } else {
            return "${robotName} is Docked/Charging"
        }
    } else if(current_phase == "hmUsrDock") {
        return "${robotName} is returning home"
    } else if(current_phase == "run") {
        return "${robotName} is cleaning (${current_cycle} cycle)"
    } else if(current_phase == "pause" || current_phase == "stop") {
        return "Paused - 'Dock' or 'Resume'?"
    }

    log.error "Unknown phase - ${current_phase}."
    return "Error - refresh to continue. Code changes required if problem persists."
}




/* local REST gw support */

def lanEventHandler(evt) {
	log.trace "GOT HERE"
    def description = evt.description
    def hub = evt?.hubId
	def parsedEvent = parseLanMessage(description)
	log.trace "RECEIVED LAN EVENT: $parsedEvent"
	
/*   
    //ping response
    if (parsedEvent.data && parsedEvent.data.service && (parsedEvent.data.service == "hch")) {
	    def msg = parsedEvent.data
        if (msg.result == "pong") {
        	//log in successful to local server
            log.info "Successfully contacted local server"
			atomicState.hchPong = true
        }   	
    }
    
    */    
}

private local_get(path, cbk) {
    def host = "$roomba_host:$roomba_port"

	sendHubCommand(new physicalgraph.device.HubAction("""GET $path HTTP/1.1\r\nHOST: $host\r\n\r\n""", physicalgraph.device.Protocol.LAN, null, [callback: cbk])) 
}

void local_dummy_cbk(physicalgraph.device.HubResponse hubResponse) {
}

void local_poll_cbk(physicalgraph.device.HubResponse hubResponse) {
	//log.debug "hubResponse: ${hubResponse.class}"
    //log.debug hubResponse.dump()
    def data = hubResponse.json
    def current_charge = data.batPct
    def robotName = data.name
	state.robotName = robotName    
    def mission = data.cleanMissionStatus
    def current_cycle = mission.cycle
    def current_phase = mission.phase
    def current_sqft = mission.sqft
    def num_mins_running = mission.mssnM
    def readyCode = mission.notReady
    def num_cleaning_jobs = mission.nMssn
    def num_dirt_detected = data.bbrun.nScrubs
    def total_job_time = data.bbrun.hr
    
  
    

    def new_status = get_robot_status(current_phase, current_cycle, current_charge, readyCode)
    def roomba_value = get_robot_enum(current_phase, readyCode)
    log.debug("Robot updates -- 2 ${roomba_value} + ${new_status}")
    //Set robotCleanerMovement state
  	 if(roomba_value == "cleaning"){
    	state.robotCleanerMovement = "cleaning"
    } else if(roomba_value == "paused"){
    	state.robotCleanerMovement = "idle"
    } else if (roomba_value == "docked"){
       state.robotCleanerMovement = "charging"
    } else if (roomba_value == "docking"){
	   state.robotCleanerMovement = "homing"
    } else if (roomba_value == "error"){
       state.robotCleanerMovement = "alarm"
    } else if (roomba_value == "bin-full") {
       state.robotCleanerMovement = "alarm"
    }
    
    //TODO def carpet_boost = get state from api
    //TODO Set state.robotCleanerTurboMode
	
    //TODO def cleaning_passes = get state from api
    //TODO Set state.robotCleanerCleaningMode
  

  
    //Set the state object
    if(roomba_value == "cleaning") {
        state.switch = "on"
    } else {
        state.switch = "off"
    }   

    /* Consumable state-changes */
    if(roomba_value == "bin-full") {
        state.consumable = "maintenance_required"
    } else if(roomba_value == "error"){
        state.consumable = "missing"
    } else {
        state.consumable = "good"
    }

    /* Timed Session state-changes */
    if(roomba_value == "cleaning") {
        state.sessionStatus = "running"
    } else if (roomba_value == "paused") {
        state.sessionStatus = "paused"
    } else if (roomba_value == "docked" || roomba_value == "docking") {
        state.sessionStatus = "canceled"
    } else {
        state.sessionStatus = "stopped"
    }

    /* Misc. state-changes */
    if(state.lastRuntime == -1) {
        state.timeRemaining = -1
    } else {
        state.timeRemaining = (state.lastRuntime ?: num_mins_running) - num_mins_running
    }
    if(state.lastSqft == -1) {
        state.sqftRemaining = -1
    } else {
        state.sqftRemaining = (state.lastSqft ?: current_sqft) - current_sqft
    }

    /*send events, display final event*/
    sendEvent(name: "robotName", value: robotName, displayed: false)
    sendEvent(name: "runtimeMins", value: num_mins_running, displayed: false)
    sendEvent(name: "runtimeSqft", value: current_sqft, displayed: false)
    sendEvent(name: "timeRemaining", value: state.timeRemaining, displayed: false)
    sendEvent(name: "sqftRemaining", value: state.sqftRemaining, displayed: false)
    sendEvent(name: "totalJobHrs", value: total_job_time, displayed: false)
    sendEvent(name: "totalJobs", value: num_cleaning_jobs, displayed: false)
    sendEvent(name: "battery", value: current_charge, displayed: false)
    sendEvent(name: "headline", value: new_status, displayed: false)
    sendEvent(name: "status", value: roomba_value)
    sendEvent(name: "switch", value: state.switch)
    sendEvent(name: "sessionStatus", value: state.sessionStatus)
    sendEvent(name: "consumable", value: state.consumable)    
    sendEvent(name: 'robotCleanerMovement', value: state.robotCleanerMovement)
    //TODO sendEvent(name: 'robotCleanerTurboMode', value: 'state.robotCleanerTurboMode')
    //TODO sendEvent(name: 'robotCleanerCleaningMode', value: 'state.robotCleanerCleaningMode')
    //sendEvent(name: "robotIpAddress", value: 'data.netinfo.addr')
}

//TODO private local_carpetBoost_auto
//TODO private local_carpetBoost_performance
//TODO private local_carpetBoost_eco
	  
//TODO private local_cleaningPasses_auto
//TODO private local_cleaningPasses_one
//TODO private local_CleaningPasses_two

private local_poll() {
	local_get('/api/local/config/preferences', 'local_poll_cbk')
}

private local_start() {
	local_get('/api/local/action/start', 'local_dummy_cbk')
}

private local_stop() {
	local_get('/api/local/action/stop', 'local_dummy_cbk')
}

private local_pause() {
	local_get('/api/local/action/pause', 'local_dummy_cbk')
}

private local_resume() {
	local_get('/api/local/action/resume', 'local_dummy_cbk')
}

private local_dock() {
	local_get('/api/local/action/dock', 'local_dummy_cbk')
}

private local_pauseAndDock() {
	local_get('/api/local/action/pause', 'local_dummy_cbk')
    pause(1000)
	local_get('/api/local/action/dock', 'local_dummy_cbk')
}

//Get the IP address of robot
private getRobotAddress(){
	return state.robotIpAddress
}

def updateDeviceNetworkID() {
	log.debug "Executing 'updateDeviceNetworkID'"
    def iphex = convertIPtoHex(roomba_host).toUpperCase()
    def porthex = convertPortToHex(roomba_port).toUpperCase()
	device.setDeviceNetworkId(iphex + ":" + porthex)
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    //log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
    return hex
}

private String convertPortToHex(port) {
    String hexport = port.toString().format( '%04x', port.toInteger() )
    //log.debug hexport
    return hexport
}
