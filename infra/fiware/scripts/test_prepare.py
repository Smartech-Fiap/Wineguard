"""Testes offline: dados privados, topicos e contratos; sem Docker ou rede."""

import json
import unittest

from prepare import build_files, render, validated


class PrepareTests(unittest.TestCase):
    def setUp(self):
        self.settings = {
            "FIWARE_SERVICE": "vinheria", "FIWARE_SERVICE_PATH": "/",
            "IOTA_API_KEY": "chave-ficticia", "DEVICE_ID": "wine002",
            "ENTITY_ID": "WineSensor002", "ENTITY_TYPE": "WineSensor",
            "MQTT_USERNAME": "usuario_teste", "MQTT_TLS_HOST": "192.0.2.10",
        }

    def test_topics_allow_telemetry_commands_and_prefixed_results(self):
        acl = build_files(self.settings)["mosquitto.acl"]
        self.assertIn("topic write /chave-ficticia/wine002/attrs\n", acl)
        self.assertIn("topic read /chave-ficticia/wine002/cmd\n", acl)
        self.assertIn("topic write /ul/chave-ficticia/wine002/cmdexe\n", acl)
        self.assertNotIn("#", acl)
        self.assertNotIn("+", acl)

    def test_rejects_topic_wildcards_and_newline_injection(self):
        for key in ("IOTA_API_KEY", "DEVICE_ID", "MQTT_USERNAME"):
            for value in ("+", "#", "ok\ntopic read #"):
                with self.subTest(key=key, value=value):
                    with self.assertRaises(ValueError):
                        build_files({**self.settings, key: value})

    def test_missing_key_does_not_fall_back_to_deployed_credential(self):
        with self.assertRaisesRegex(ValueError, "IOTA_API_KEY"):
            build_files({**self.settings, "IOTA_API_KEY": ""})

    def test_device_has_config_telemetry_and_both_commands(self):
        payload = json.loads(build_files(self.settings)["device.json"])
        device = payload["devices"][0]
        self.assertEqual(device["entity_name"], "WineSensor002")
        self.assertIn({"object_id": "cfg", "name": "configuration", "type": "Text"},
                      device["attributes"])
        self.assertEqual({item["name"] for item in device["commands"]}, {"alert", "configure"})

    def test_subscription_keeps_legacy_format_and_internal_callback(self):
        payload = json.loads(build_files(self.settings)["history-subscription.json"])
        self.assertEqual(payload["notification"]["attrsFormat"], "legacy")
        self.assertEqual(payload["notification"]["http"]["url"], "http://sth-comet:8666/notify")
        self.assertEqual(payload["notification"]["attrs"], ["temperature", "humidity", "luminosity"])
        self.assertTrue(payload["subject"]["condition"]["notifyOnMetadataChange"])

    def test_tls_san_distinguishes_ip_from_dns_and_rejects_injected_config(self):
        self.assertEqual(validated(self.settings)["TLS_SAN"], "IP:192.0.2.10")
        self.assertEqual(validated({**self.settings, "MQTT_TLS_HOST": "broker.example.org"})["TLS_SAN"],
                         "DNS:broker.example.org")
        with self.assertRaises(ValueError):
            validated({**self.settings, "MQTT_TLS_HOST": "broker.example.org\n[other]"})

    def test_substitution_is_structural_not_raw_json_interpolation(self):
        value = 'aspas " e barra \\ no valor'
        payload = render({"field": "${VALUE}"}, {"VALUE": value})
        self.assertEqual(json.loads(json.dumps(payload))["field"], value)


if __name__ == "__main__":
    unittest.main()
