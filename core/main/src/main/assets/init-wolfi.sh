set -e  # Exit immediately on Failure

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin
export HOME=/root

if [ ! -s /etc/resolv.conf ]; then
    echo "nameserver 8.8.8.8" > /etc/resolv.conf
fi


export PS1='\[\033[01;32m\]\u@rewolf\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
# shellcheck disable=SC2034
export PIP_BREAK_SYSTEM_PACKAGES=1

#fix linker warning
if [ ! -f /linkerconfig/ld.config.txt ];then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi

if [ "$#" -eq 0 ]; then
    if [ -f /etc/profile ]; then
        source /etc/profile
    fi
    export PS1='\[\033[01;32m\]\u@rewolf\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
    cd $HOME
    if [ -f /initrc ]; then
        source /initrc
    fi
    : "${LOGIN_SHELL:=/bin/sh}"
    export SHELL="$LOGIN_SHELL"
    if [ -x "$LOGIN_SHELL" ]; then
        exec "$LOGIN_SHELL"
    else
        exec /bin/sh
    fi
else
    exec "$@"
fi
