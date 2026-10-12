import hashlib
import os
import secrets
import socket
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


def discover_apps():
    """Discover launchable .lnk shortcuts in Start Menu and Desktop folders."""
    roots = [
        Path(os.environ.get("APPDATA", "")) / "Microsoft/Windows/Start Menu/Programs",
        Path(os.environ.get("PROGRAMDATA", "C:/ProgramData")) / "Microsoft/Windows/Start Menu/Programs",
        Path(os.environ.get("USERPROFILE", "")) / "Desktop",
        Path(os.environ.get("PUBLIC", "C:/Users/Public")) / "Desktop",
    ]
    found = {}
    for root in roots:
        if not root.exists():
            continue
        try:
            for shortcut in root.rglob("*.lnk"):
                name = shortcut.stem.strip()
                if not name or name.lower().startswith(("uninstall", "readme")):
                    continue
                app_id = hashlib.sha256(str(shortcut.resolve()).lower().encode("utf-8")).hexdigest()[:16]
                found[app_id] = {"id": app_id, "name": name, "path": str(shortcut)}
        except (OSError, PermissionError):
            continue
    APP_SHORTCUTS.clear()
    APP_SHORTCUTS.update(found)
    return sorted(({"id": a["id"], "name": a["name"]} for a in found.values()), key=lambda a: a["name"].casefold())


@app.after_request
def no_cache(response):
    response.headers["Cache-Control"] = "no-store"
    return response


def paired():
    supplied = request.headers.get("Authorization", "")
    return secrets.compare_digest(supplied, f"Bearer {TOKEN}")


@app.get("/health")
def health():
    return jsonify({"app": "WiDeck", "version": 2})


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
    shortcut = APP_SHORTCUTS.get(app_id)
    if not shortcut:
        discover_apps()
        shortcut = APP_SHORTCUTS.get(app_id)
    if not shortcut:
        return jsonify({"error": "Unknown app. Refresh the app list."}), 404
    try:
        os.startfile(shortcut["path"])
    except OSError:
        app.logger.exception("Could not launch shortcut")
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
    root.geometry("470x340")
    root.resizable(False, False)
    root.configure(bg="#10141b")
    tk.Label(root, text="WiDeck", font=("Segoe UI", 24, "bold"), fg="#f3f5f8", bg="#10141b").pack(pady=(18, 4))
    tk.Label(root, text="Android remote host", font=("Segoe UI", 10), fg="#9ba7b7", bg="#10141b").pack()
    tk.Label(root, text=f"Laptop IP   {local_ip()}:{PORT}", font=("Consolas", 12), fg="#dce5ef", bg="#10141b").pack(pady=(16, 6))
    tk.Label(root, text=f"Pairing PIN   {PIN}", font=("Consolas", 18, "bold"), fg="#71e0b5", bg="#10141b").pack(pady=4)
    tk.Label(root, text=f"{len(APP_SHORTCUTS)} Start Menu/Desktop shortcuts found", font=("Segoe UI", 10), fg="#dce5ef", bg="#10141b").pack(pady=6)
    tk.Label(root, text="Connect your phone first, then choose up to five apps.\nBoth devices must use the same Wi-Fi. Keep this window open.", font=("Segoe UI", 9), fg="#9ba7b7", bg="#10141b", justify="center").pack(pady=10)
    root.mainloop()


if __name__ == "__main__":
    main()
