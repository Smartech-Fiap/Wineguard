"""Gera arquivos privados locais. Nao chama APIs nem inicia containers."""

import argparse
import ipaddress
import json
import os
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def require(settings, key):
    value = settings.get(key, "")
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Exporte a variavel obrigatoria {key}.")
    return value


def validated(settings):
    result = {}
    patterns = {
        "FIWARE_SERVICE": r"[A-Za-z0-9_-]{1,100}",
        "FIWARE_SERVICE_PATH": r"/[A-Za-z0-9_/-]*",
        "IOTA_API_KEY": r"[A-Za-z0-9_-]{1,128}",
        "DEVICE_ID": r"[A-Za-z0-9_-]{1,100}",
        "ENTITY_ID": r"[A-Za-z0-9_.-]{1,255}",
        "ENTITY_TYPE": r"[A-Za-z0-9_.-]{1,100}",
        "MQTT_USERNAME": r"[A-Za-z0-9_.-]{1,100}",
    }
    for key, pattern in patterns.items():
        value = require(settings, key)
        if not re.fullmatch(pattern, value):
            raise ValueError(f"Formato nao aceito para {key}; confira o README.")
        result[key] = value
    host = require(settings, "MQTT_TLS_HOST")
    try:
        address = ipaddress.ip_address(host)
        result["TLS_SAN"] = "IP:" + str(address)
    except ValueError:
        labels = host.split(".")
        if len(host) > 253 or not all(re.fullmatch(
                r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", label)
                for label in labels):
            raise ValueError("MQTT_TLS_HOST deve ser um IP ou nome DNS valido.") from None
        result["TLS_SAN"] = "DNS:" + host
    return result


def render(value, settings):
    if isinstance(value, list):
        return [render(item, settings) for item in value]
    if isinstance(value, dict):
        return {key: render(item, settings) for key, item in value.items()}
    if isinstance(value, str):
        match = re.fullmatch(r"\$\{([A-Z_]+)\}", value)
        if match:
            return require(settings, match.group(1))
    return value


def build_files(settings, root=ROOT):
    values = validated(settings)
    files = {}
    for kind in ("group", "device", "history-subscription"):
        template = json.loads(
            (root / "provisioning" / f"{kind}.template.json").read_text(encoding="utf-8"))
        payload = render(template, values)
        files[f"{kind}.json"] = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    prefix = f"/{values['IOTA_API_KEY']}/{values['DEVICE_ID']}"
    files["mosquitto.acl"] = (
        f"user {values['MQTT_USERNAME']}\n"
        f"topic write {prefix}/attrs\n"
        f"topic read {prefix}/cmd\n"
        f"topic write /ul{prefix}/cmdexe\n"
    )
    files["server.cnf"] = (
        "[req]\nprompt = no\ndistinguished_name = dn\nreq_extensions = server_ext\n"
        "[dn]\nCN = WineGuard MQTT\n"
        "[server_ext]\nbasicConstraints = critical,CA:FALSE\n"
        "keyUsage = critical,digitalSignature,keyEncipherment\n"
        "extendedKeyUsage = serverAuth\n"
        f"subjectAltName = {values['TLS_SAN']}\n"
    )
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replace", action="store_true",
                        help="Substitui somente os arquivos gerados; nao altera senha ou certificados.")
    args = parser.parse_args()
    try:
        files = build_files(os.environ)
        output = ROOT / ".local"
        if output.is_symlink():
            raise ValueError(".local nao pode ser um link simbolico.")
        if not args.replace and any((output / name).exists() for name in files):
            raise ValueError("Arquivos gerados ja existem; revise antes de usar --replace.")
        output.mkdir(mode=0o700, exist_ok=True)
        for name, text in files.items():
            target = output / name
            if target.is_symlink():
                raise ValueError("Um arquivo de destino e link simbolico; operacao interrompida.")
            # 0600 desde a criacao; group.json e ACL incluem a chave do grupo.
            descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
            try:
                os.chmod(target, 0o600)
                with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
                    descriptor = None
                    stream.write(text)
            finally:
                if descriptor is not None:
                    os.close(descriptor)
        print("Arquivos gerados em .local/. Nenhum servico foi iniciado ou provisionado.")
    except (ValueError, OSError) as error:
        parser.exit(1, f"Erro: {error}\n")


if __name__ == "__main__":
    main()
