// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hardwarekeys;

import java.util.*;
import me.jxl.kiosk.plugins.PluginHost;

public final class HardwareKeysTest {
    static final class Host implements PluginHost {
        final Set<String> subscriptions = new HashSet<>();
        final List<Map<String, Object>> calls = new ArrayList<>();
        String status = "";
        boolean statusError;
        boolean failCalls;
        public void subscribe(String event) { subscriptions.add(event); }
        public void unsubscribe(String event) { subscriptions.remove(event); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) { status = message; statusError = error; }
        public void haCall(Map<String, Object> message, PluginHost.CommandCallback callback) {
            calls.add(message);
            if (failCalls) callback.onResult(false, null, "unauthorized: no admin");
            else callback.onResult(true, Collections.emptyMap(), null);
        }
    }

    static Map<String, Object> key(String name, String action, int repeat) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", name);
        payload.put("code", 131);
        payload.put("scanCode", 59);
        payload.put("action", action);
        payload.put("repeat", repeat);
        return payload;
    }

    public static void main(String[] args) {
        Host host = new Host();
        HardwareKeysPlugin plugin = new HardwareKeysPlugin();
        plugin.start(host, new HashMap<>());
        assert host.subscriptions.contains("device.key");

        // A press is forwarded once, as a fire_event message without an id.
        plugin.onEvent("ks.device.key", key("F1", "down", 0));
        assert host.calls.size() == 1;
        Map<String, Object> message = host.calls.get(0);
        assert "fire_event".equals(message.get("type"));
        assert "kiosk_satellite_key".equals(message.get("event_type"));
        assert !message.containsKey("id");
        Map<?, ?> data = (Map<?, ?>) message.get("event_data");
        assert "F1".equals(data.get("key"));
        assert Integer.valueOf(131).equals(data.get("code"));
        assert Integer.valueOf(59).equals(data.get("scan_code"));
        assert "kiosk-satellite".equals(data.get("source"));
        assert host.status.contains("F1") && !host.statusError;

        // Release, repeats of a held key and keys that are not listed are ignored.
        plugin.onEvent("ks.device.key", key("F1", "up", 0));
        plugin.onEvent("ks.device.key", key("F1", "down", 3));
        plugin.onEvent("ks.device.key", key("VOLUME_UP", "down", 0));
        plugin.onEvent("ks.other", key("F2", "down", 0));
        assert host.calls.size() == 1;

        // Settings: another key list, event type and source. KEYCODE_ prefix and case do not matter.
        Map<String, Object> settings = new HashMap<>();
        settings.put("keys", "keycode_volume_up, f2");
        settings.put("eventType", "hallway_key");
        settings.put("source", "hallway-wall");
        plugin.configure(settings);
        plugin.onEvent("ks.device.key", key("VOLUME_UP", "down", 0));
        plugin.onEvent("ks.device.key", key("F2", "down", 0));
        plugin.onEvent("ks.device.key", key("F1", "down", 0));
        assert host.calls.size() == 3;
        assert "hallway_key".equals(host.calls.get(2).get("event_type"));
        assert "hallway-wall".equals(((Map<?, ?>) host.calls.get(2).get("event_data")).get("source"));

        // An empty list forwards every key.
        settings.put("keys", "");
        plugin.configure(settings);
        plugin.onEvent("ks.device.key", key("F1", "down", 0));
        assert host.calls.size() == 4;

        // A failed call is shown as an error.
        host.failCalls = true;
        plugin.onEvent("ks.device.key", key("F1", "down", 0));
        assert host.statusError && host.status.contains("unauthorized");

        // After stop nothing is sent.
        plugin.stop();
        host.calls.clear();
        plugin.onEvent("ks.device.key", key("F1", "down", 0));
        assert host.calls.isEmpty();
        System.out.println("HardwareKeysTest ok");
    }
}
