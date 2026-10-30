# z_file module – provenance and licence

This module is the ZFileManager file picker as modified for ZeroTermux. VictorSuite builds it from source
instead of the prebuilt `app/libs/z_file.aar` it used before.

- **Base:** ZFileManager by zippo88888888 – https://github.com/zippo88888888/ZFileManager – Apache License 2.0
  (as stated by the upstream README; https://www.apache.org/licenses/LICENSE-2.0).
- **Modifications:** ZeroTermux by hanxinhao000 (`com.zp.z_file.victor.*`, `InstallTarData`, `ModuleInstallUtils`,
  `InstallModuleDialog`, backup/restore dialogs, resources) – GPL-3.0.
- **Source taken from:** the `z_file/` directory of https://github.com/msmt2017/new_ZeroTermux at commit `f68fd87`.
  The upstream source files carry no per-file licence headers; none were removed.
- **VictorSuite changes** (GPL-3.0), to build with the project's toolchain and to match the shipped `z_file.aar`:
  - resolved the unresolved git merge markers left in that commit, using the side that matches the aar's classes
    (`build.gradle`, `AndroidManifest.xml`, `LogUtils`, `ZFileUUtils`, `ZFileListFragment`, `ModuleInstallUtils`, layouts);
  - new `build.gradle` (namespace `com.zp.z_file`, project SDK levels, `com.hzy:un7zip` pinned to `1.7.1`, unused
    `codeeditor`/kapt dependencies and the bundled duplicate `libs/classes.jar` of `com.hzy.lib7z` dropped);
  - activities are `android:exported="false"` (the aar declared none, which also meant not exported);
  - restored from the aar where `f68fd87` was older: `type/ZFileSHType` (+ `.sh` mapping and `ic_zfile_sh.png`),
    `ZFileListFragment.checkHasPermissionDoNotDisturb()`, `ZFileUUtils.writerFile` returning `boolean`,
    the `zfile_select_menu_pls` string, and the aar versions of `item_zfile_list_file.xml` and `dialog_switch.xml`;
  - Kotlin 2.x fixes: `toLowerCase/toUpperCase(Locale)` → `lowercase/uppercase(Locale)`, non-null `RadioGroup` override.

The whole module is distributed under GPL-3.0 as part of VictorSuite; the Apache-2.0 terms continue to apply to the
ZFileManager-derived parts.
