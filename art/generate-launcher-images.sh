#!/bin/sh
# Regenerate VictorSuite launcher mipmaps, adaptive XMLs, and the themed
# monochrome glyph from art/ic_launcher_source.png (the existing artwork at
# 1024px). Historical Termux SVG sources (art/ic_launcher.svg and
# drawable/ic_foreground.xml) are kept for reference and are not used as the
# app icon.
set -e -u
cd "$(dirname "$0")"
# potracer + numpy are required to rebuild the monochrome vector.
python3 generate-victorsuite-launcher.py
