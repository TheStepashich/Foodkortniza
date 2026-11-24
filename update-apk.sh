#!/usr/bin/env bash
# Ветка gh-pages: кладёт уже собранный APK в downloads/.
set -euo pipefail
cd "$(dirname "$0")"

if [ "$#" -ne 1 ]; then
  echo "Использование: ./update-apk.sh /путь/к/app-release.apk" >&2
  exit 1
fi

cp "$1" downloads/foodkortniza.apk
echo "APK обновлён: downloads/foodkortniza.apk"
