# hassalarm
Android app for integration with Hass.io as a sensor for the next scheduled alarm on the device.

Expect that alarm clocks schedule alarms properly which will trigger the system wide `ACTION_NEXT_ALARM_CLOCK_CHANGED`.
Once that happen, a call to your Hass.io instance will eventually be made.

## Usage
1. Clone the repo and build the app: `./gradlew installDebug`
2. Open it and setup your hostname and API key.
3. Schedule an alarm in any of your alarm apps

Once your device have a network connection, it should eventually do a call to the Hass.io API, and a `sensor.next_alarm` should popup.

You can then use this for your automations. This is an example where the scene `wakeup` will be called 3 minutes before the scheduled alarm.
```yaml
  trigger:
  - minutes: /1
    platform: time
    seconds: 0
  condition:
  - condition: template
    value_template: '{{ (((as_timestamp(now()) | int) + 3*60) | timestamp_custom("%Y-%m-%d %H:%M:00")) == states.sensor.next_alarm.state }}'
  action:
  - data:
      entity_id: scene.wakeup
    service: scene.turn_on
```