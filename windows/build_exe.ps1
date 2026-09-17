$ErrorActionPreference = "Stop"
py -m pip install --upgrade pip
py -m pip install -r requirements.txt
py -m pip install pyinstaller==6.15.0
pyinstaller --noconfirm --clean --onefile --windowed --name NillTracker nill_tracker.py
Write-Host "Built dist\NillTracker.exe"
