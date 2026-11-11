#!/system/bin/sh
# Run on the phone as root before uninstalling a differently signed module.
set -eu
[ "$(id -u)" = 0 ] || { echo 'Run through su first.' >&2; exit 1; }
DEST=${1:-/data/local/tmp/lyric-module-backup-$(date +%Y%m%d-%H%M%S)}
umask 077
mkdir -p "$DEST"
LIST="$DEST/paths.txt"
: > "$LIST"
for package in com.hchen.superlyric io.github.proify.lyricon; do
    for folder in \
        "data/user/0/$package/shared_prefs" \
        "data/user_de/0/$package/shared_prefs" \
        "data/misc/lsposed/prefs/$package" \
        "data/adb/lspd/config/prefs/$package"; do
        [ ! -d "/$folder" ] || printf '%s\n' "$folder" >> "$LIST"
    done
done
[ -s "$LIST" ] || { echo 'No preference directories found; do not uninstall yet.' >&2; exit 1; }
tar -C / -cf "$DEST/preferences.tar" -T "$LIST"
chmod 600 "$DEST/preferences.tar" "$LIST"
echo "Backup created in $DEST"
echo 'Copy this private backup somewhere safe. It can contain API keys; do not publish it.'
echo 'This script does not uninstall apps or restore old owners, scopes, or certificates.'
