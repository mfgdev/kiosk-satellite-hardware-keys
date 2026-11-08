# Hardware Keys to Home Assistant

A plugin for [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) that forwards the hardware buttons of the device to Home Assistant as events.

It was written for the **Shelly Wall Display XL**, whose four front buttons only reach the Shelly app while that app is in the foreground. With Kiosk Satellite in front the buttons were dead. Android still reports them as the keys `F1` to `F4`, so this plugin reads them through Kiosk Satellite and fires a Home Assistant event for every press.

## What it does

For each first press of an allowed key the plugin fires this event on the Home Assistant event bus:

```yaml
event_type: kiosk_satellite_key
event_data:
  key: F1          # Android key name without KEYCODE_
  code: 131        # Android key code
  scan_code: 59    # hardware scan code
  source: kiosk-satellite
```

Releasing a key and the repeats of a held key do not fire events, so a long press is one event.

## Requirements

- Kiosk Satellite 2026.10.45 or newer, with **Enable Plugins** turned on.
- Kiosk Satellite must be connected to Home Assistant with a token that may fire events (an administrator user, the usual long-lived token).
- Kiosk Satellite must be in front. Key events do not arrive while another app is in front or the screen is off.

## Install

1. In Kiosk Satellite open **Settings > Plugin Manager** and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste `https://github.com/mfgdev/kiosk-satellite-hardware-keys` and choose **Preview**.
3. Read the manifest and choose **Trust and install**, then enable **Hardware Keys to Home Assistant**.

For a local build use **Plugin Manager > Developer Tools > Install from ZIP** with the ZIP from the latest release.

## Settings

| Setting | Default | Meaning |
| --- | --- | --- |
| Keys to forward | `F1,F2,F3,F4` | Comma-separated Android key names without `KEYCODE_`, for example `F1,F2` or `VOLUME_UP`. Empty forwards every key. |
| Home Assistant event type | `kiosk_satellite_key` | Event type fired for each press. |
| Source name | `kiosk-satellite` | Sent as `event_data.source`, to tell several kiosks apart. |

## Example automation

```yaml
triggers:
  - trigger: event
    event_type: kiosk_satellite_key
    event_data:
      key: F1
actions:
  - action: light.toggle
    target:
      entity_id: light.hallway
```

On a Shelly Wall Display XL the keys are `F1` to `F4`, from top to bottom (check this on your device with the event listener in Home Assistant developer tools).

## Build

Needs Python 3, JDK 17 or newer and an Android SDK with build-tools (set `ANDROID_HOME`).

```sh
python3 tools/test.py
python3 tools/build.py
```

The ZIP lands in `dist/`. A GitHub release runs the included workflow, which attaches the manifest, ZIP and checksum that Kiosk Satellite installs.

## License

Apache-2.0. The `sdk/` sources are the plugin SDK interfaces of the [Kiosk Satellite plugin template](https://github.com/jxlarrea/kiosk-satellite-plugins), also Apache-2.0.
