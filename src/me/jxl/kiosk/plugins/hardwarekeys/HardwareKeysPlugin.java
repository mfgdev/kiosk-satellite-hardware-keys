// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hardwarekeys;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Forwards the hardware keys of the device to Home Assistant as events.
 *
 * Every first press of an allowed key fires one event of the configured type on the Home Assistant event bus:
 * {@code event_type: kiosk_satellite_key}, {@code event_data: {key, code, scan_code, source}}.
 * Held keys are not repeated.
 */
public final class HardwareKeysPlugin implements KioskPlugin {
    static final String DEFAULT_EVENT_TYPE = "kiosk_satellite_key";
    static final String DEFAULT_KEYS = "F1,F2,F3,F4";

    private PluginHost host;
    private String eventType = DEFAULT_EVENT_TYPE;
    private Set<String> allowedKeys = parseKeys(DEFAULT_KEYS);
    private String source = "kiosk-satellite";
    private int sent;

    @Override public void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        sent = 0;
        configure(settings);
        host.subscribe("device.key");
        host.status("Waiting for key presses: " + describeKeys(), false);
    }

    @Override public void configure(Map<String, Object> settings) {
        String type = text(settings.get("eventType"), DEFAULT_EVENT_TYPE);
        eventType = type.isEmpty() ? DEFAULT_EVENT_TYPE : type;
        allowedKeys = parseKeys(text(settings.get("keys"), DEFAULT_KEYS));
        String src = text(settings.get("source"), "kiosk-satellite");
        source = src.isEmpty() ? "kiosk-satellite" : src;
    }

    @Override public void execute(String command, Map<String, Object> arguments) {
        throw new IllegalArgumentException("This plugin has no actions");
    }

    @Override public void onEvent(String event, Map<String, Object> payload) {
        if (host == null || !"ks.device.key".equals(event)) return;
        // One event per press: skip the release and the repeats of a held key.
        if (!"down".equals(payload.get("action"))) return;
        Object repeat = payload.get("repeat");
        if (repeat instanceof Number && ((Number) repeat).intValue() != 0) return;
        String key = String.valueOf(payload.get("key"));
        if (!allowedKeys.isEmpty() && !allowedKeys.contains(key.toUpperCase())) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("code", payload.get("code"));
        data.put("scan_code", payload.get("scanCode"));
        data.put("source", source);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "fire_event");
        message.put("event_type", eventType);
        message.put("event_data", data);

        final PluginHost current = host;
        host.haCall(message, (ok, result, error) -> {
            if (!ok) {
                current.status("Event failed for " + key + ": " + error, true);
            } else {
                sent++;
                current.status("Last key " + key + " sent as " + eventType + " (" + sent + " total)", false);
            }
        });
    }

    @Override public void stop() {
        host = null;
        sent = 0;
    }

    private String describeKeys() {
        return allowedKeys.isEmpty() ? "all keys" : String.join(", ", allowedKeys);
    }

    static Set<String> parseKeys(String list) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        for (String part : list.split(",")) {
            String key = part.trim().toUpperCase();
            if (key.startsWith("KEYCODE_")) key = key.substring("KEYCODE_".length());
            if (!key.isEmpty()) keys.add(key);
        }
        return new HashSet<>(keys.isEmpty() ? new java.util.LinkedHashSet<String>() : keys);
    }

    private static String text(Object value, String fallback) {
        return value instanceof String ? ((String) value).trim() : fallback;
    }
}
