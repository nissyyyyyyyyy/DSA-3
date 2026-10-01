#!/bin/bash
cd "$(dirname "$0")"
javac -d bin src/main/java/spellcheck/*.java && java -cp bin spellcheck.Main
read -p "Press Enter to close..."
