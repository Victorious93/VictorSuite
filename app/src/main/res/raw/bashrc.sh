#!/system/bin/sh
#################VictorSuite###########
#    VictorSuite Shell 增加会话脚本     #
######################################
pid=$$
shell_VictorSuite() {
echo ">>>>>>>>>>>>>>>>>shell_VictorSuite"
export PREFIX='/data/data/com.victorious.suite/files/usr'
export HOME='/data/data/com.victorious.suite/files/home'
export LD_LIBRARY_PATH='/data/data/com.victorious.suite/files/usr/lib'
export PATH="/data/data/com.victorious.suite/files/usr/bin:/data/data/com.victorious.suite/files/usr/bin/applets:$PATH"
export LANG='en_US.UTF-8'
export SHELL='/data/data/com.victorious.suite/files/usr/bin/bash'
cd "$HOME"
exec "$SHELL" -l "./.timerdir/termux_timer.sh"
}
shell_Android() {
echo ">>>>>>>>>>>>>>>>>shell_Android"
chmod 777 /data/data/com.victorious.suite/files/home/.timerdir/shell_timer.sh
./data/data/com.victorious.suite/files/home/.timerdir/shell_timer.sh
}

shell_kill() {
echo ">>>>>>>>>>>>>>>>>shell_kill"
kill -9 pid
}
shell_chmod() {
    chmod 777 /data/data/com.victorious.suite/files/execTermuxEnv.sh
}
