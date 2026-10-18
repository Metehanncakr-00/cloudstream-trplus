#!/usr/bin/env python3
"""
install_scrapling.py

Installs Scrapling, its fetcher dependencies, and browser engines for
headless testing and anti-bot handling.
Usage:
    python tools/install_scrapling.py [--skip-browsers] [--browsers-only]
"""

import sys
import subprocess
import argparse

def run_command(cmd, desc):
    print(f"[*] {desc}: {' '.join(cmd)}")
    try:
        subprocess.check_call(cmd)
        print(f"[+] {desc} succeeded.")
        return True
    except subprocess.CalledProcessError as e:
        print(f"[-] {desc} failed with exit code {e.returncode}.", file=sys.stderr)
        return False

def main():
    parser = argparse.ArgumentParser(description="Install Scrapling and browser dependencies")
    parser.add_argument("--skip-browsers", action="store_true", help="Skip installing patchright/playwright browsers")
    parser.add_argument("--browsers-only", action="store_true", help="Only install browser engines, skip pip requirements")
    args = parser.parse_args()

    py_executable = sys.executable

    # 1. Install pip requirements (unless --browsers-only)
    if not args.browsers_only:
        success = run_command(
            [py_executable, "-m", "pip", "install", "-r", "tools/requirements.txt"],
            "Installing Python dependencies"
        )
        if not success:
            sys.exit(1)

    # 2. Install patchright/playwright chromium browser
    if not args.skip_browsers:
        print("[*] Installing browser binaries for patchright...")
        cmd_patchright = [py_executable, "-m", "patchright", "install", "chromium"]
        if sys.platform.startswith("linux"):
            cmd_patchright.append("--with-deps")

        p_success = run_command(cmd_patchright, "Installing Patchright Chromium")
        if not p_success:
            # Fallback to playwright
            print("[!] Patchright install failed, trying playwright install...")
            cmd_playwright = [py_executable, "-m", "playwright", "install", "chromium"]
            if sys.platform.startswith("linux"):
                cmd_playwright.append("--with-deps")
            pw_success = run_command(cmd_playwright, "Installing Playwright Chromium")
            if not pw_success:
                print("[-] Browser installation failed.", file=sys.stderr)
                sys.exit(1)

    # 3. Verify Scrapling import
    print("[*] Verifying Scrapling installation...")
    try:
        import scrapling
        from scrapling import Fetcher, DynamicFetcher, StealthyFetcher
        print(f"[+] Scrapling {scrapling.__version__} is ready.")
    except Exception as e:
        print(f"[-] Scrapling verification failed: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
