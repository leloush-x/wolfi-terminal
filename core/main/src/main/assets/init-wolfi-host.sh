WOLFI_DIR=$PREFIX/local/wolfi

mkdir -p $WOLFI_DIR

if [ -z "$(ls -A "$WOLFI_DIR" | grep -vE '^(root|tmp)$')" ]; then
    tar -xf "$PREFIX/files/wolfi.tar.gz" -C "$WOLFI_DIR"
fi

if [ -f "$BIN/rm" ]; then
    rm -f "$WOLFI_DIR/bin/rm"
    cp "$BIN/rm" "$WOLFI_DIR/bin/rm"
    chmod +x "$WOLFI_DIR/bin/rm"
fi

ARGS="--kill-on-exit"
ARGS="$ARGS -w /"

for system_mnt in /apex /odm /product /system /system_ext /vendor \
 /linkerconfig/ld.config.txt \
 /linkerconfig/com.android.art/ld.config.txt \
 /plat_property_contexts /property_contexts; do

 if [ -e "$system_mnt" ]; then
  system_mnt=$(realpath "$system_mnt")
  ARGS="$ARGS -b ${system_mnt}"
 fi
done
unset system_mnt

ARGS="$ARGS -b /sdcard"
ARGS="$ARGS -b /storage"
ARGS="$ARGS -b /dev"
ARGS="$ARGS -b /data"
ARGS="$ARGS -b /dev/urandom:/dev/random"
ARGS="$ARGS -b /proc"
ARGS="$ARGS -b $PREFIX"
ARGS="$ARGS -b $PREFIX/local/stat:/proc/stat"
ARGS="$ARGS -b $PREFIX/local/vmstat:/proc/vmstat"

if [ -e "/proc/self/fd" ]; then
  ARGS="$ARGS -b /proc/self/fd:/dev/fd"
fi

if [ -e "/proc/self/fd/0" ]; then
  ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"
fi

if [ -e "/proc/self/fd/1" ]; then
  ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"
fi

if [ -e "/proc/self/fd/2" ]; then
  ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"
fi


ARGS="$ARGS -b $PREFIX"
ARGS="$ARGS -b /sys"

if [ ! -d "$PREFIX/local/wolfi/tmp" ]; then
 mkdir -p "$PREFIX/local/wolfi/tmp"
 chmod 1777 "$PREFIX/local/wolfi/tmp"
fi
ARGS="$ARGS -b $PREFIX/local/wolfi/tmp:/dev/shm"

ARGS="$ARGS -r $PREFIX/local/wolfi"
ARGS="$ARGS -0"
ARGS="$ARGS --link2symlink"
ARGS="$ARGS --sysvipc"
ARGS="$ARGS -L"

$PROOT $ARGS sh $PREFIX/local/bin/init-wolfi "$@"
