# hassalarm
Android app for integration with Hass.io / Home Assistant as an `input_datetime` for the next scheduled alarm on the device.

Expect that alarm clocks schedule alarms properly which will trigger the system wide `ACTION_NEXT_ALARM_CLOCK_CHANGED`.
Once that happen, a call to your Hass.io instance will happen within an hour, given that there is an Internet connection. On failure, the Android OS will retry later.

## Home Assistant setup
1. Require Home Assistant 2020.12.1 or later.
1. Add a `input_datetime` with both date and time in your `configuration.yaml`
  ```
  input_datetime:
    next_alarm:
      name: Next scheduled alarm
      has_date: true
      has_time: true
  ```
1. Add a time sensor in your `configuration.yaml`:
  ```
  sensor:
  - platform: time_date
    display_options:
      - 'date_time'
  ```
1. If you want the value to persist on Home Assistant restarts, enable the [History](https://www.home-assistant.io/integrations/history/) and [Recorder](https://www.home-assistant.io/integrations/recorder) components.
1. Add some automation for your new input:
  ```yaml
  automation:
    trigger:
      platform: template
      value_template: "{{ states('sensor.date_time') == (state_attr('input_datetime.next_alarm', 'timestamp') | int | timestamp_custom('%Y-%m-%d, %H:%M', True)) }}"
    action:
      service: light.turn_on
      entity_id: light.bedroom
  ```

Or if you want to trigger an automation five minutes before the alarm will go off:
```yaml
  automation:
    trigger:
      platform: template
      value_template: "{{ ((as_timestamp(states('sensor.date_time').replace(',','')) | int) + 5*60) == (state_attr('input_datetime.next_alarm', 'timestamp') | int)  }}"

    action:
      service: light.turn_on
      entity_id: light.bedroom
```

## Webhook support
HassAlarm supports updates through a webhook. This requires some setup on the Home Assistant side, but it greatly reduces the permissions the app has in Home Assistant.
To use a webhook for HassAlarm updates, you can use the automation below and adapt it as necessary. Note that your webhook ID should be hard to guess.

This automation will update ànd set the `input_datetime` for the entity ID specified in the app. Then you can use the `input_datetime` sensor in your automations as in the example above.
```yaml
automation:
  trigger:
    platform: webhook
    webhook_id: <your webhook id>
  action:
    service: input_datetime.set_datetime
    data_template:
      entity_id: "{{ trigger.json.entity_id }}"
      timestamp: "{{ trigger.json.timestamp }}"
```

## App usage
1. Install via [Google Play Store](https://play.google.com/store/apps/details?id=com.fjun.hassalarm) or clone the repo and build the app: `./gradlew installDebug`
1. Create a [long lived token](https://www.home-assistant.io/docs/authentication/#your-account-profile) on your profile or a webhook automation in Home Assistant.
1. Open the app and setup your hostname, longed live token and input_datetime entity ID: `input_datetime.next_alarm`
1. Schedule an alarm in any of your alarm apps

Once your device have a network connection, it should eventually do a call to the Hass.io API and your input_datetime should be set.

## Troubleshooting
1. Make sure the app is allowed to run in the background (e.g. start a sync job), read more here: https://support.google.com/pixelphone/thread/6068458?hl=en (thanks [@Hooolm](https://github.com/Hooolm))
2. Make sure to use a fairly recent version of Home Assistant, 2020.12.1 or later. 2020.12.0 have bugs with input_datetime
3. If the time reported in Home Assistant is off by hours or minutes, first try using The stock [Google Alarm Clock](https://play.google.com/store/apps/details?id=com.google.android.deskclock). There are known bugs with the Samsung [[1]](https://eu.community.samsung.com/t5/galaxy-s9-s9/pie-clock-app-bug/td-p/927333) and Xiaomi [[2]](https://community.openhab.org/t/xiaomi-miui-alarm-clock-sending-wrong-epoch-value/90734)[[3]](https://c.mi.com/thread-2585135-1-0.html) alarm clock, and probably others. If there are still issues when using the [Google Alarm Clock](https://play.google.com/store/apps/details?id=com.google.android.deskclock), please open a [issue here](https://github.com/Johboh/hassalarm/issues) on GitHub.

## Build status
[![Build Status](https://travis-ci.com/Johboh/hassalarm.svg?branch=master)](https://travis-ci.com/Johboh/hassalarm)
