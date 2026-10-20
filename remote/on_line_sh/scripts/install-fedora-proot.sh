#!/data/data/com.termux/files/usr/bin/sh
#
# Install a minimal Fedora root filesystem under proot and generate a
# "startfedora" launcher.
#
# Ported from an older reference build and translated to
# English. It now stops at the first failed step instead of writing a launcher
# for a rootfs that never downloaded or extracted. The image mirror below is a
# third-party host, not a VictorSuite-controlled one — verify it still
# resolves and that you trust its contents before running this.

# Image mirror (GitLab). A GitHub mirror of the same image also exists
# upstream but was not re-verified here; swap ADDRESS if you prefer it.
export ADDRESS=https://gitlab.com/zhanghua000/fedora-termux-cn/raw/master/fedora.7z

set -e

echo "Installing required packages: proot tar wget git p7zip"
pkg install proot tar wget git p7zip -y

echo "Creating local directory..."
mkdir -p ~/fedora
cd ~/fedora || exit 1

echo "Downloading and extracting the image..."
wget "$ADDRESS" -O fedora.7z
7z x -y fedora.7z

echo "Cleaning up temporary files..."
rm -f fedora.7z

echo "Generating launch script..."
cat > /data/data/com.termux/files/usr/bin/startfedora <<- EOM
#!/bin/sh
unset LD_PRELOAD && proot --link2symlink -0 -r ~/fedora -b /dev/ -b /sys/ -b /proc/ -b /storage/ -b \$HOME -w \$HOME /bin/env -i HOME=/root TERM="\$TERM" PS1='[termux@fedora \W]\$ ' LANG=en_US.UTF8 PATH=/bin:/usr/bin:/sbin:/usr/sbin /bin/zsh -l
EOM
chmod +x /data/data/com.termux/files/usr/bin/startfedora

echo "Done! Run 'startfedora' to start Fedora. Inside the Fedora shell, run 'dnf update' to update it."
