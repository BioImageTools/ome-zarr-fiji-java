#!/bin/sh
# Start a virtual X server (Xvfb) on display :99.
# This is required for headless environments (like GitHub CI)
# when running tools that require a graphical display.
export DISPLAY=:99
sudo Xvfb -ac :99 -screen 0 1280x1024x24 > /dev/null 2>&1 &

# Install blosc system library
# Required for building/running code that depends on libblosc.
sudo apt-get update
sudo apt-get install -y libblosc-dev

# Download and execute the SciJava CI build script
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-build.sh
sh ci-build.sh
