package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_COMMON_FUNCTIONS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.victor.util.UUtils;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.code.CodeString;
import com.termux.victor.dialog.LoadingDialog;
import com.termux.victor.popuwindow.MenuLeftPopuListWindow;
import com.termux.victor.url.FileUrl;
import com.termux.victor.utermux_windows.qemu.activity.RunWindowActivity;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class QEMUClickConfig extends BaseMenuClickConfig implements MenuLeftPopuListWindow.ItemClickPopuListener {
    @Override
    public int getType() {
        return CODE_COMMON_FUNCTIONS;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.windows);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.QEMU);
    }

    @Override
    public void onClick(View view, Context context) {
        ArrayList<MenuLeftPopuListWindow.MenuLeftPopuListData> menuLeftPopuListData1 = new ArrayList<>();
        //官方
        MenuLeftPopuListWindow.MenuLeftPopuListData qemuData = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.qemu_ico_hai, UUtils.getString(R.string.vs_qemu_of_the_sea), 5);
        menuLeftPopuListData1.add(qemuData);

        MenuLeftPopuListWindow.MenuLeftPopuListData windowsData = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.windows_xp, UUtils.getString(R.string.vs_qemu_windows), 501);
        menuLeftPopuListData1.add(windowsData);

        MenuLeftPopuListWindow.MenuLeftPopuListData win7Data = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.windows, UUtils.getString(R.string.vs_win7_simulation), 502);
        menuLeftPopuListData1.add(win7Data);

        MenuLeftPopuListWindow.MenuLeftPopuListData winXpData = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.windows_xp_ico, UUtils.getString(R.string.WinXp), 503);
        menuLeftPopuListData1.add(winXpData);

        showMenuDialog(menuLeftPopuListData1, view, context);
    }

    private void showMenuDialog(ArrayList<MenuLeftPopuListWindow.MenuLeftPopuListData> arrayList, View showView, Context context) {
        MenuLeftPopuListWindow menuLeftPopuListWindow = new MenuLeftPopuListWindow(context);
        menuLeftPopuListWindow.setItemClickPopuListener(this);
        menuLeftPopuListWindow.setListData(arrayList);
        menuLeftPopuListWindow.showAsDropDown(showView, 250, -200);
    }

    @Override
    public void itemClick(int id, int index, @Nullable MenuLeftPopuListWindow mMenuLeftPopuListWindow) {
        switch (id) {
            case 5: {
                // Always use the script bundled in the app's own assets rather than
                // fetching and executing a script from a third-party server at runtime.
                LoadingDialog loadingDialog = new LoadingDialog(mContext);
                loadingDialog.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        UUtils.writerFile("qemu/utqemu.sh", new File(FileUrl.INSTANCE.getMainHomeUrl(), "/utqemu.sh"));
                        ((Activity) mContext).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(CodeString.INSTANCE.getRunQemuSh());
                                loadingDialog.dismiss();
                            }
                        });
                    }
                }).start();
                break;
            }

            case 501:
                mContext.startActivity(new Intent(mContext, RunWindowActivity.class));
                break;
            case 502:
                XXPermissions.with(mContext)
                    .permission(Permission.WRITE_EXTERNAL_STORAGE)
                    .permission(Permission.READ_EXTERNAL_STORAGE)
                    .request(new OnPermissionCallback() {

                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                File victorSuiteShare = FileUrl.INSTANCE.getVictorSuiteShare();
                                if (!victorSuiteShare.exists()) {
                                    victorSuiteShare.mkdirs();
                                }
                                UUtils.writerFile("qemu/qemu_win7.sh", new File(FileUrl.INSTANCE.getMainHomeUrl(), "/qemu_win7.sh"));
                                com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(CodeString.INSTANCE.getRunWin7Sh());
                            } else {
                                UUtils.showMsg(UUtils.getString(R.string.no_permission));
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                UUtils.showMsg(UUtils.getString(R.string.no_permission));
                                // 如果是被永久拒绝就跳转到应用权限系统设置页面
                                XXPermissions.startPermissionActivity(mContext, permissions);
                            } else {
                                UUtils.showMsg(UUtils.getString(R.string.no_permission));
                            }
                        }
                    });
                break;

            case 503:
                XXPermissions.with(mContext)
                    .permission(Permission.WRITE_EXTERNAL_STORAGE)
                    .permission(Permission.READ_EXTERNAL_STORAGE)
                    .request(new OnPermissionCallback() {

                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                File victorSuiteShare = FileUrl.INSTANCE.getVictorSuiteShare();
                                if (!victorSuiteShare.exists()) {
                                    victorSuiteShare.mkdirs();
                                }
                                UUtils.writerFile("qemu/qemu_winxp.sh", new File(FileUrl.INSTANCE.getMainHomeUrl(), "/qemu_winxp.sh"));
                                com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(CodeString.INSTANCE.getRunWinXPSh());
                            } else {
                                UUtils.showMsg(UUtils.getString(R.string.no_permission));
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                UUtils.showMsg(UUtils.getString(R.string.no_permission));
                                // 如果是被永久拒绝就跳转到应用权限系统设置页面
                                XXPermissions.startPermissionActivity(mContext, permissions);
                            } else {
                                UUtils.showMsg(UUtils.getString(R.string.no_permission));
                            }
                        }
                    });
                break;
        }
    }
}
