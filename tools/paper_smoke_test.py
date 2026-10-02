"""Check an already started local Paper server over its configured RCON connection.

Usage: python tools/paper_smoke_test.py --server ../../server --stop
Credentials are read locally and never printed. No player data is modified.
"""
import argparse
import hashlib
import socket
import struct
import time
from pathlib import Path


def receive(stream):
    def exact(size):
        data = b""
        while len(data) < size:
            chunk = stream.recv(size - len(data))
            if not chunk:
                raise ConnectionError("RCON closed the connection")
            data += chunk
        return data
    length = struct.unpack("<i", exact(4))[0]
    packet = exact(length)
    request, kind = struct.unpack("<ii", packet[:8])
    return request, kind, packet[8:-2].decode("utf-8", errors="replace")


def send(stream, request, kind, text):
    data = struct.pack("<ii", request, kind) + text.encode("utf-8") + b"\0\0"
    stream.sendall(struct.pack("<i", len(data)) + data)


def command(properties, text):
    with socket.create_connection(("127.0.0.1", int(properties.get("rcon.port", "25575"))), timeout=10) as stream:
        send(stream, 1, 3, properties["rcon.password"])
        while True:
            request, kind, response = receive(stream)
            if request == -1:
                raise RuntimeError("RCON authentication failed")
            if kind == 2:
                break
        send(stream, 2, 2, text)
        while True:
            request, kind, response = receive(stream)
            if request == 2:
                return response


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", type=Path, required=True)
    parser.add_argument("--stop", action="store_true")
    parser.add_argument("--expected-version", default="1.3.0")
    args = parser.parse_args()
    root = args.server.resolve()
    properties = {}
    for line in (root / "server.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            properties[key] = value
    log = root / "logs/latest.log"
    deadline = time.monotonic() + 50
    while time.monotonic() < deadline:
        content = log.read_text(encoding="utf-8", errors="replace") if log.exists() else ""
        if "Done (" in content and f"TotalXPRewards v{args.expected_version}" in content:
            # latest.log may still contain the previous run during early startup.
            try:
                if args.expected_version in command(properties, "version TotalXPRewards"):
                    break
            except (OSError, ConnectionError):
                pass
        time.sleep(1)
    else:
        raise RuntimeError(f"Paper with TotalXPRewards {args.expected_version} did not become ready")
    # Startup may intentionally migrate the config; reload must leave it unchanged.
    protected = [root / "plugins/TotalXPRewards" / name for name in ("config.yml", "ranks.yml", "lang.yml")]
    hashes = {p: hashlib.sha256(p.read_bytes()).hexdigest() for p in protected}
    try:
        for text in ("version TotalXPRewards", "totalxp", "totalxp reload", "totalxp get @a", "totalxp status @a",
                     "totalxp set @a -1", "totalxp show", "totalxp hide"):
            response = command(properties, text)
            print(f"{text}: {response}", flush=True)
            if "version TotalXPRewards" == text and args.expected_version not in response:
                raise RuntimeError("Unexpected plugin version")
            if text == "totalxp reload" and "Configuration reloaded." not in response:
                raise RuntimeError("Plugin reload failed")
            if "Exception" in response or "Unknown command" in response:
                raise RuntimeError(f"Command failed: {text}")
        for path, expected in hashes.items():
            assert hashlib.sha256(path.read_bytes()).hexdigest() == expected, f"Changed: {path.name}"
        print("PASS: startup, console/RCON commands, reload, config/lang preservation", flush=True)
    finally:
        if args.stop:
            print(command(properties, "stop"), flush=True)


if __name__ == "__main__":
    main()
