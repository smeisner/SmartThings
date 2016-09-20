# SmartThings - Garage Door Watcher

This SmartApp will watch a garage door sensor for it being open. If it is open, and it's after sunset, and before sunrise,
start sending push notifications and/or SMS messages to the owner. After a number of warnings, at predetermined  intervals,
close the garage door. If sunset occurs prior to closing the door, stop notifying and stop watching the door status. At the
next sunset, start watchig the door again.

Notes:
- When the SmartApp is instantiated (first run), the sunset time is fetched. If sunset has not occured yet, schedule the
  door watcher to start running at sunset.
- If sunset has already occured today, do not schedule the watcher. It will get scheduled just after midnight.
- If the door is opened, a routine is called to check if it's night time (after sunset). If it is, the door watcher
  gets scheduled.
- If the door is closed, the same routine is used, but it cancels scheduled door watcher routine.
- To never close the door, you can set the notification interval and notification count enough to exceed the time
  before sunrise. Meaning, if sunset is at 16:00 and sunrise is 7:00, that's a span of 15 hours (winter nights can be long).
  If you scheduled 31 notifications at every 30 minutes, sunrise would occur prior to the door getting closed.
  