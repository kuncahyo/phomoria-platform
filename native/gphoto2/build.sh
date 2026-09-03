#!/usr/bin/env bash
set -euo pipefail
mkdir -p ../../tools/gphoto2
gcc -Wall -Wextra -O2 phomoria-gphoto2-camera.c -o ../../tools/gphoto2/phomoria-gphoto2-camera.exe \
    $(pkg-config --cflags --libs libgphoto2) -pthread
echo "Built ../../tools/gphoto2/phomoria-gphoto2-camera.exe"
