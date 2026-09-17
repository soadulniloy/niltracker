import json
import os
import queue
import sys
import threading
import time
import webbrowser
from datetime import datetime, timezone
from pathlib import Path

import pystray
import requests
from PIL import Image, ImageDraw
import tkinter as tk
from tkinter import messagebox

APP_DIR = Path(os.environ.get("APPDATA", Path.home())) / "NillTracker"
CONFIG_FILE = APP_DIR / "config.json"
QUEUE_FILE = APP_DIR / "pending.json"

DEFAULTS = {
    "url": "",
    "token": "",
    "device": "Windows PC"
}

state_lock = threading.Lock()
running = False
start_time = None
icon = None
settings_window = None


def ensure_dir():
    APP_DIR.mkdir(parents=True, exist_ok=True)


def load_json(path, default):
    ensure_dir()
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return default.copy() if isinstance(default, dict) else list(default)


def save_json(path, data):
    ensure_dir()
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")


def config():
    c = DEFAULTS.copy()
    c.update(load_json(CONFIG_FILE, {}))
    return c


def format_seconds(seconds):
    seconds = max(0, int(seconds))
    return f"{seconds // 3600:02d}:{(seconds % 3600) // 60:02d}:{seconds % 60:02d}"


def make_icon():
    im = Image.new("RGBA", (64, 64), (18, 18, 24, 255))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((6, 6, 58, 58), radius=14, fill=(32, 32, 42, 255))
    d.arc((16, 16, 48, 48), -80, 260, fill=(120, 220, 180, 255), width=4)
    d.line((32, 32, 32, 21), fill=(240, 240, 245, 255), width=4)
    d.line((32, 32, 41, 37), fill=(240, 240, 245, 255), width=4)
    return im


def post_session(start, end):
    c = config()
    if not c["url"] or not c["token"]:
        enqueue(start, end)
        return False

    payload = {
        "action": "log",
        "token": c["token"],
        "start": start.isoformat(),
        "end": end.isoformat(),
        "durationSeconds": int((end - start).total_seconds()),
        "device": c["device"]
    }
    try:
        r = requests.post(c["url"], json=payload, timeout=15)
        data = r.json()
        if data.get("ok"):
            return True
    except Exception:
        pass
    enqueue(start, end)
    return False


def enqueue(start, end):
    q = load_json(QUEUE_FILE, [])
    q.append({
        "start": start.isoformat(),
        "end": end.isoformat(),
        "durationSeconds": int((end - start).total_seconds())
    })
    save_json(QUEUE_FILE, q)


def flush_queue():
    c = config()
    if not c["url"] or not c["token"]:
        return
    q = load_json(QUEUE_FILE, [])
    remaining = []
    for item in q:
        try:
            s = datetime.fromisoformat(item["start"])
            e = datetime.fromisoformat(item["end"])
            if not post_session_direct(c, s, e, item["durationSeconds"]):
                remaining.append(item)
        except Exception:
            remaining.append(item)
    save_json(QUEUE_FILE, remaining)


def post_session_direct(c, start, end, duration):
    try:
        r = requests.post(c["url"], json={
            "action": "log",
            "token": c["token"],
            "start": start.isoformat(),
            "end": end.isoformat(),
            "durationSeconds": duration,
            "device": c["device"]
        }, timeout=15)
        return bool(r.json().get("ok"))
    except Exception:
        return False


def toggle():
    global running, start_time
    with state_lock:
        if not running:
            running = True
            start_time = datetime.now(timezone.utc)
        else:
            end = datetime.now(timezone.utc)
            s = start_time
            running = False
            start_time = None
            threading.Thread(target=post_session, args=(s, end), daemon=True).start()
    update_menu()


def current_text():
    with state_lock:
        if not running or not start_time:
            return "Start study timer"
        elapsed = (datetime.now(timezone.utc) - start_time).total_seconds()
        return f"Stop • {format_seconds(elapsed)}"


def update_menu():
    if icon:
        icon.title = f"Nill Tracker — {current_text()}"
        icon.menu = pystray.Menu(
            pystray.MenuItem(current_text(), lambda: toggle()),
            pystray.MenuItem("Settings", lambda: open_settings()),
            pystray.MenuItem("Open Google Sheet", lambda: open_sheet()),
            pystray.MenuItem("Exit", lambda: quit_app())
        )


def open_sheet():
    c = config()
    if c["url"]:
        webbrowser.open(c["url"])


def open_settings():
    global settings_window
    if settings_window is not None:
        try:
            settings_window.deiconify()
            settings_window.lift()
            return
        except Exception:
            pass

    root = tk.Tk()
    settings_window = root
    root.title("Nill Tracker Settings")
    root.geometry("520x270")
    root.configure(bg="#121218")
    root.resizable(False, False)

    c = config()
    fields = {}
    labels = [("Google Apps Script Web App URL", "url"),
              ("Secret token", "token"),
              ("Device name", "device")]

    for i, (label, key) in enumerate(labels):
        tk.Label(root, text=label, bg="#121218", fg="#eeeeF5",
                 anchor="w").place(x=25, y=25 + i*65)
        ent = tk.Entry(root, bg="#20202a", fg="#f2f2f7",
                       insertbackground="#ffffff", relief="flat",
                       show="*" if key == "token" else "")
        ent.insert(0, c.get(key, ""))
        ent.place(x=25, y=48 + i*65, width=470, height=28)
        fields[key] = ent

    def save():
        new = {k: fields[k].get().strip() for _, k in labels}
        save_json(CONFIG_FILE, new)
        messagebox.showinfo("Nill Tracker", "Settings saved.")
        flush_queue()
        root.destroy()

    tk.Button(root, text="Save", command=save, bg="#78dcb4", fg="#101014",
              relief="flat", font=("Segoe UI", 10, "bold")).place(
                  x=25, y=225, width=110, height=32)

    root.protocol("WM_DELETE_WINDOW", root.withdraw)
    root.mainloop()
    settings_window = None


def quit_app():
    global icon
    if icon:
        icon.stop()


def heartbeat():
    while True:
        time.sleep(1)
        if icon:
            update_menu()


def main():
    global icon
    ensure_dir()
    icon = pystray.Icon("NillTracker", make_icon(), "Nill Tracker")
    update_menu()
    threading.Thread(target=flush_queue, daemon=True).start()
    threading.Thread(target=heartbeat, daemon=True).start()
    icon.run()


if __name__ == "__main__":
    main()
