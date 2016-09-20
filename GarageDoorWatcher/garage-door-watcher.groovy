/**
 *  Garage Door Watcher
 *
 *  Author: Steve Meisner
 *
 *  Date: 2016-09-19
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

definition(
    name: "Garage Watcher",
    namespace: "meisners.net",
    author: "Steve Meisner",
    description: "If garage door left open, begin telling user after sunset. After notifications, automatically close door.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png"
)

preferences {
    section("Choose garage door to watch") {
        input "doorContact", "capability.contactSensor", title: "Which Sensor?"
        input "theDoor", "capability.doorControl", title: "Which Door?"
    }
    section ("Sunset offset (optional)") {
        input "sunsetOffsetValue", "text", title: "HH:MM", required: false
        input "sunsetOffsetDir", "enum", title: "Before or After", required: false, metadata: [values: ["Before","After"]]
    }
    section ("Zip code (optional, defaults to location coordinates)") {
        input "zipCode", "text", title: "Zip Code", required: false
    }
    section( "Notifications" ) {
        input "message", "text", title: "Push message to send:", required: false
        input "smsPhone", "phone", title: "Phone number to notify via SMS", required: false
        input "smsMessage", "text", title: "SMS message to send:", required: false
    }
    section ("Frequency of notifications") {
        input "notificationDelay", "number", title: "Delay between notifications", required: true
        input "notificationCount", "number", title: "How many notifications prior to door close?", required: true
    }
}

def installed() {
    log.debug "Entering installed()"
    initialize()
    log.debug "Running astroCheck() once"
    astroCheck()
    subscribe(doorContact, "contact", doorOpened)
    subscribe(location, "sunset", sunsetHandler)
    subscribe(location, "sunrise", sunriseHandler)
}

def updated() {
    log.debug "Entering updated()"
    initialize()
}

def initialize() {
    log.debug "Entering initialize()"
    state.NotificationCount = 0
    scheduleAstroCheck()
}

def sunsetHandler(evt) {
    log.debug "Sun has set!"
    state.isNightTime = true
}

def sunriseHandler(evt) {
    log.debug "Sun has risen!"
    state.isNightTime = false
}

def scheduleAstroCheck() {
    log.debug "Entering scheduleAstroCheck()"
    def min = Math.round(Math.floor(Math.random() * 60))
    def exp = "0 $min * * * ?"
    log.debug "scheduleAstroCheck: $exp"
    unschedule (astroCheck)
    schedule(exp, astroCheck) // check every hour since location can change without event?
}

def astroCheck() {
    log.debug "Entering astroCheck()"
    def s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: sunriseOffset, sunsetOffset: sunsetOffset)
    def sunsetTime = s.sunset
    def sunriseTime = s.sunrise
    def now = new Date()

    if (state.sunsetTime != sunsetTime.time) {
        //
        // If the above is true, we have changed over the day.
        // Now "sunset" is referring to the next day sunset. So
        // we need to update it. Chances are, it's just past midnight.
        //
        state.sunsetTime = sunsetTime.time

        unschedule("doorChecker")

        //
        // If sunset time is after now (we haven't hit sunset yet)...
        //
        if (sunsetTime.after(now)) {
            log.debug "Scheduling sunset handler to run at $sunsetTime"
            runOnce(sunsetTime, doorChecker)
        }
    }

    //
    // Update the 'daytime' flag. Check against the updated
    // sunrise and sunset times. This test is really needed
    // as a starting point for the 'isNightTime' flag. Otherwise,
    // the sunrise & sunset callbacks will be used.
    //
    if (now.time < sunsetTime.time && now.time > sunriseTime.time) {
        state.isNightTime = false
    } else {
        state.isNightTime = true
    }
    log.debug "Current isNightTime = $state.isNightTime"
}

def doorChecker() {
    //
    // Sunset just occured or the door was opened
    //
    log.debug "Entering doorChecker()"
    def now = new Date()
    if (state.isNightTime) {
        log.debug "After sunset; Checking garage door"
        def Gdoor = checkGarage()
        log.debug "Door is $Gdoor"
        if (Gdoor == "open") {
            state.NotificationCount = state.NotificationCount + 1
            if (state.NotificationCount > notificationCount) {
                log.debug "Notified enough times. Closing door"
                state.NotificationCount = 0
                send("Closing door now: $theDoor.name")
                theDoor.close()
                unschedule("doorChecker")
            } else {
                log.debug "Notifying user and deferring"
                send(message)
                scheduledoorChecker()
            }
        } else {
            log.debug "Door is now closed - stop monitoring door"
            state.NotificationCount = 0
            unschedule("doorChecker")
        }
    } else {
        log.debug "It's daytime!! Stop checking door"
        unschedule("doorChecker")
    }
}

def doorOpened(evt) {
    // Callback when door opened or closed
    log.debug "Entering doorChanged($evt.value)"
    if (evt.value == "open") {
        log.debug "Door opened"
        if (state.isNightTime) {
            log.debug "Between sunset and sunrise; scheduling watcher"
            scheduledoorChecker()
        }
    } else {
        log.debug "Door closed -- descheduling callbacks"
        state.NotificationCount = 0
        unschedule ("checkDoor")
        unschedule ("doorChecker")
    }
}

def scheduledoorChecker() {
    log.debug "Entering scheduledoorChecker()"
    def exp = now() + (60000 * notificationDelay)
    log.debug "schedule Door Check time: $exp"
    unschedule("doorChecker")
    schedule(exp, doorChecker)
}

private send(msg) {
    if (message != null) {
        log.info ("Sending push message: $message")
        sendPush(message)
    }
    if (smsMessage != null) {
        log.info ("Sending SMS to $smsPhone : $smsMessage")
        sendSmsMessage (smsPhone, smsMessage)
    }
}

def checkGarage(evt) {
    def latestValue = doorContact.currentContact
}

private getSunsetOffset() {
    sunsetOffsetValue ? (sunsetOffsetDir == "Before" ? "-$sunsetOffsetValue" : sunsetOffsetValue) : null
}
