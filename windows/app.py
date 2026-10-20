import hashlib
import json
import os
import secrets
import socket
import subprocess
import threading
from pathlib import Path
import tkinter as tk

from flask import Flask, jsonify, request
import pyautogui

PORT = 47821
PIN = f"{secrets.randbelow(900000) + 100000}"
app = Flask(__name__)
TOKEN = secrets.token_urlsafe(24)
APP_SHORTCUTS = {}

ACTIONS = {
    "play_pause": lambda: pyautogui.press("playpause"),
    "volume_up": lambda: pyautogui.press("volumeup"),
    "volume_down": lambda: pyautogui.press("volumedown"),
    "mute": lambda: pyautogui.press("volumemute"),
}


def stable_id(kind, value):
    return hashlib.sha256(f"{kind}:{value}".casefold().encode("utf-8")).hexdigest()[:16]


def discover_apps():
    """Discover Start Menu shortcuts and registered Windows apps, including Store apps."""
    # Keep one entry per display name. Registered AppsFolder IDs replace same-name
    # shortcuts because they can launch packaged apps such as the Microsoft Store Spotify.
    found = {}
    roots = [
        Path(os.environ.get("APPDATA", "")) / "Microsoft/Windows/Start Menu/Programs",
        Path(os.environ.get("PROGRAMDATA", "C:/ProgramData")) / "Microsoft/Windows/Start Menu/Programs",
        Path(os.environ.get("USERPROFILE", "")) / "Desktop",
        Path(os.environ.get("PUBLIC", "C:/Users/Public")) / "Desktop",
    ]

    for root in roots:
        if not root.exists():
            continue
        try:
            for shortcut in root.rglob("*.lnk"):
                name = shortcut.stem.strip()
                if not name or name.casefold().startswith(("uninstall", "readme")):
                    continue
                target = str(shortcut)
                app_id = stable_id("shortcut", str(shortcut.resolve()))
                found[name.casefold()] = {
                    "id": app_id,
                    "name": name,
                    "kind": "shortcut",
                    "target": target,
                }
        except (OSError, PermissionError):
            continue

    # Get-StartApps returns registered Win32 and Microsoft Store apps, even when
    # Windows does not expose a normal .lnk shortcut in the searched folders.
    try:
        result = subprocess.run(
            [
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-Command", "Get-StartApps | Select-Object Name,AppID | ConvertTo-Json -Compress",
            ],
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
        )
        if result.returncode == 0 and result.stdout.strip():
            entries = json.loads(result.stdout)
            if isinstance(entries, dict):
                entries = [entries]
            if isinstance(entries, list):
                for entry in entries:
                    name = str(entry.get("Name") or "").strip()
                    windows_app_id = str(entry.get("AppID") or "").strip()
                    if not name or not windows_app_id:
                        continue
                    app_id = stable_id("startapp", windows_app_id)
                    found[name.casefold()] = {
                        "id": app_id,
                        "name": name,
                        "kind": "startapp",
                        "target": windows_app_id,
                    }
    except (OSError, subprocess.SubprocessError, json.JSONDecodeError, TypeError, ValueError):
        app.logger.warning("Could not query registered Windows apps", exc_info=True)

    APP_SHORTCUTS.clear()
    APP_SHORTCUTS.update({entry["id"]: entry for entry in found.values()})
    return sorted(
        ({"id": entry["id"], "name": entry["name"]} for entry in found.values()),
        key=lambda entry: entry["name"].casefold(),
    )


@app.after_request
def no_cache(response):
    response.headers["Cache-Control"] = "no-store"
    return response


def paired():
    supplied = request.headers.get("Authorization", "")
    return secrets.compare_digest(supplied, f"Bearer {TOKEN}")


@app.get("/health")
def health():
    return jsonify({"app": "WiDeck", "version": 3})


@app.post("/pair")
def pair():
    data = request.get_json(silent=True) or {}
    if not secrets.compare_digest(str(data.get("pin", "")), PIN):
        return jsonify({"error": "Invalid PIN"}), 401
    return jsonify({"token": TOKEN})


@app.get("/apps")
def apps():
    if not paired():
        return jsonify({"error": "Not paired"}), 401
    return jsonify({"apps": discover_apps()})


@app.post("/launch")
def launch():
    if not paired():
        return jsonify({"error": "Not paired"}), 401
    data = request.get_json(silent=True) or {}
    app_id = str(data.get("app_id", ""))
    entry = APP_SHORTCUTS.get(app_id)
    if not entry:
        discover_apps()
        entry = APP_SHORTCUTS.get(app_id)
    if not entry:
        return jsonify({"error": "Unknown app. Refresh the app list."}), 404
    try:
        if entry["kind"] == "startapp":
            subprocess.Popen(["explorer.exe", "shell:AppsFolder\\" + entry["target"]])
        else:
            os.startfile(entry["target"])
    except (OSError, subprocess.SubprocessError, KeyError):
        app.logger.exception("Could not launch app %s", entry.get("name"))
        return jsonify({"error": "App could not be launched"}), 500
    return jsonify({"ok": True})


@app.post("/action")
def action():
    if not paired():
        return jsonify({"error": "Not paired"}), 401
    data = request.get_json(silent=True) or {}
    name = data.get("action")
    if name not in ACTIONS:
        return jsonify({"error": "Unknown action"}), 400
    try:
        ACTIONS[name]()
    except Exception:
        app.logger.exception("Action failed")
        return jsonify({"error": "Action failed"}), 500
    return jsonify({"ok": True})




@app.post("/macro")
def run_macro():
    if not paired():
        return jsonify({"error": "Not paired"}), 401
    data = request.get_json(silent=True) or {}
    steps = data.get("steps")
    if not isinstance(steps, list) or not steps or len(steps) > 12:
        return jsonify({"error": "A macro must contain between 1 and 12 steps"}), 400
    # Validate the complete macro before executing any step.
    validated = []
    for step in steps:
        if not isinstance(step, dict):
            return jsonify({"error": "Invalid macro step"}), 400
        kind = step.get("type")
        if kind == "launch":
            app_id = str(step.get("app_id", ""))
            entry = APP_SHORTCUTS.get(app_id)
            if not entry:
                discover_apps()
                entry = APP_SHORTCUTS.get(app_id)
            if not entry:
                return jsonify({"error": "Macro references an unknown app"}), 400
            validated.append(("launch", entry))
        elif kind == "url":
            target = str(step.get("url", "")).strip()
            from urllib.parse import urlparse
            parsed = urlparse(target)
            if parsed.scheme not in ("http", "https") or not parsed.netloc:
                return jsonify({"error": "Only valid HTTP/HTTPS URLs are allowed"}), 400
            validated.append(("url", target))
        else:
            return jsonify({"error": "Unsupported macro step"}), 400
    try:
        import webbrowser
        for kind, value in validated:
            if kind == "launch":
                if value["kind"] == "startapp":
                    subprocess.Popen(["explorer.exe", "shell:AppsFolder\\" + value["target"]])
                else:
                    os.startfile(value["target"])
            else:
                webbrowser.open(value)
    except (OSError, subprocess.SubprocessError, KeyError):
        app.logger.exception("Macro execution failed")
        return jsonify({"error": "Macro execution failed"}), 500
    return jsonify({"ok": True, "steps": len(validated)})


def local_ip():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("192.0.2.1", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def serve():
    app.run(host="0.0.0.0", port=PORT, debug=False, threaded=True, use_reloader=False)


def main():
    discover_apps()
    threading.Thread(target=serve, daemon=True).start()
    root = tk.Tk()
    root.title("WiDeck · Windows Host")
    root.geometry("490x350")
    root.resizable(False, False)
    root.configure(bg="#10141b")
    tk.Label(root, text="WiDeck", font=("Segoe UI", 24, "bold"), fg="#f3f5f8", bg="#10141b").pack(pady=(18, 4))
    tk.Label(root, text="Android remote host", font=("Segoe UI", 10), fg="#9ba7b7", bg="#10141b").pack()
    tk.Label(root, text=f"Laptop IP   {local_ip()}:{PORT}", font=("Consolas", 12), fg="#dce5ef", bg="#10141b").pack(pady=(16, 6))
    tk.Label(root, text=f"Pairing PIN   {PIN}", font=("Consolas", 18, "bold"), fg="#71e0b5", bg="#10141b").pack(pady=4)
    tk.Label(root, text=f"{len(APP_SHORTCUTS)} apps and shortcuts discovered", font=("Segoe UI", 10), fg="#dce5ef", bg="#10141b").pack(pady=6)
    tk.Label(
        root,
        text="Connect your phone, then search and choose up to five apps.\n"
             "Windows Start apps, including supported Store apps, are included.\n"
             "Both devices must use the same Wi-Fi. Keep this window open.",
        font=("Segoe UI", 9),
        fg="#9ba7b7",
        bg="#10141b",
        justify="center",
    ).pack(pady=10)
    root.mainloop()


if __name__ == "__main__":
    main()
