@echo off
setlocal
python -m pip install --upgrade pip
python -m pip install -r requirements.txt pyinstaller
pyinstaller --noconfirm --clean --windowed --name "Shahab Scanner" main.py
echo Build complete: dist\Shahab Scanner\Shahab Scanner.exe
pause
