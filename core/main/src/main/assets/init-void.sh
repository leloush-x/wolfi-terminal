set -e  # Exit immediately on Failure

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin
export HOME=/root

if [ ! -s /etc/resolv.conf ]; then
    echo "nameserver 8.8.8.8" > /etc/resolv.conf
fi


export PS1='\[\033[01;32m\]\u@revoid\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
# shellcheck disable=SC2034
export PIP_BREAK_SYSTEM_PACKAGES=1

#fix linker warning
if [ ! -f /linkerconfig/ld.config.txt ];then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi

if [ "$#" -eq 0 ]; then
    if [ -f /etc/profile ]; then
        . /etc/profile
    fi
    BRANDED_PS1='\[\033[01;32m\]\u@revoid\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
    export PS1="$BRANDED_PS1"
    cd $HOME
    if [ -f /initrc ]; then
        . /initrc
    fi
    if [ -f "$HOME/.profile" ]; then
        . "$HOME/.profile"
    fi
    # Re-assert after profile/initrc (they may override PS1).
    export PS1="$BRANDED_PS1"
    : "${LOGIN_SHELL:=/bin/sh}"
    export SHELL="$LOGIN_SHELL"
    if [ -x "$LOGIN_SHELL" ]; then
        case "$LOGIN_SHELL" in
            *bash)
                # Bash sources /etc/bash.bashrc + ~/.bashrc which overwrite
                # inherited PS1, so launch via a wrapper rc that re-forces
                # the branded prompt last.
                RETERM_RC="/tmp/reterm-bashrc-$$"
                {
                    echo '# reterm branded prompt wrapper'
                    echo '[ -f /etc/bash.bashrc ] && . /etc/bash.bashrc'
                    echo '[ -f /etc/bash/bashrc ] && . /etc/bash/bashrc'
                    echo '[ -f "$HOME/.bashrc" ] && . "$HOME/.bashrc"'
                    echo "PS1='$BRANDED_PS1'"
                    echo 'export PS1'
                } > "$RETERM_RC" 2>/dev/null || true
                exec "$LOGIN_SHELL" --rcfile "$RETERM_RC" -i
                ;;
            *)
                # dash/sh do not expand bash \[ \] escapes, use simple prompt.
                export PS1='root@revoid:/# '
                exec "$LOGIN_SHELL"
                ;;
        esac
    else
        exec /bin/sh
    fi
else
    exec "$@"
fi
