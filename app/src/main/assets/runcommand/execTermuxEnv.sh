#!/system/bin/sh
#################VictorSuite###########
#    VictorSuite Shell 启动会话脚本     #
######################################
export PREFIX='/data/data/com.victorious.suite/files/usr'
export HOME='/data/data/com.victorious.suite/files/home'
export LD_LIBRARY_PATH='/data/data/com.victorious.suite/files/usr/lib'
export PATH="/data/data/com.victorious.suite/files/usr/bin:/data/data/com.victorious.suite/files/usr/bin/applets:$PATH"
export LANG='en_US.UTF-8'
export SHELL='/data/data/com.victorious.suite/files/usr/bin/bash'
cd "$HOME"
exec "$SHELL" -l

