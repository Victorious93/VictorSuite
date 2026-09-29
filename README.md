

VictorSuite is a community-driven, non-profit enhancement of the Termux ecosystem, created by merging the ZeroTermux codebase and expanding it with additional tools for Linux environment management, automation, and recovery. It is intended exclusively for personal learning, experimentation, and technical collaboration.

Rather than replacing Termux, VictorSuite builds upon it by providing a more organized, feature-rich, and user-friendly experience for running Linux on Android. Its goal is to simplify setup, improve portability, and streamline advanced workflows through an integrated suite of management tools.

> Fleet note: VictorSuite is also the third companion Android app in [Victorious93/droidcommand-AI](https://github.com/Victorious93/droidcommand-AI)'s app fleet, alongside Victorious93/hackeraiETC and Victorious93/Pentest-Swarm-AI — see that repo's `docs/ARCHITECTURE.md` §8 and `docs/AUDIT_2026-09-05.md` for status. No VictorSuite-specific code exists in droidcommand-AI, and none is planned: because VictorSuite keeps Termux's own `applicationId` (`com.termux`) and `RUN_COMMAND` contract unchanged (see Licensing and Third-Party Content below), droidcommand-AI's existing `core-termux` module already reaches it — and, for the same reason, can't tell it apart from genuine upstream Termux. Worth knowing if you maintain this repo: that shared package identity also means VictorSuite and real Termux can never be installed on the same device. Nothing else below about VictorSuite's own scope, GPL license, or Termux/ZeroTermux lineage changes because of it.

Features

- One-command installation and initial configuration.
- Backup and restoration of Termux environments and user data.
- Installation and management of multiple Linux distributions, including Ubuntu and Kali.
- Fast switching between installed Linux containers.
- Runtime repository and package source selection.
- Automated environment provisioning and maintenance.
- Modular architecture for future expansion and additional integrations.
- Optimized workflows for developers, Linux enthusiasts, security researchers, students, and advanced Android users.
- Separately distributed internal-channel X11 AAR support.
- Prebuilt APK releases.
- Shared signing key compatibility with Utermux for user convenience.

Licensing and Third-Party Content

VictorSuite is released under the GNU General Public License (GPL).

Application icons are provided by the public Alibaba Iconfont library.

Users are solely responsible for any recovery packages, modules, or other third-party software they install, modify, or distribute while using VictorSuite. This project must not be used for commercial purposes or for any unlawful activities.

Acknowledgements

VictorSuite is built upon the work of numerous open-source projects and libraries. Their contributions make this project possible, and while this list may not include every contributor, their work is sincerely appreciated.

Special thanks to:

- Termux
- Termux:Tasker
- Termux:API
- Termux:Styling
- Termux Packages
- ImagePicker (Lichenwei-Dev)
- Android VShell
- AgentWeb
- XXPermissions
- libaums
- ColorSeekBar
- ImagePicker (jeasonlzy)
- Glide
- ttyd
- File Browser
- Swiftp
- ImmersionBar
- FNetServer
- CodeEditor
- Alibaba Iconfont

Voluntary financial support for the original upstream projects may be provided through the payment methods listed in their respective repositories. We encourage users to support the maintainers whose work forms the foundation of the Termux ecosystem and its related open-source libraries.