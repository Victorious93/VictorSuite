package com.termux.victor.utils;

import android.util.Log;
import android.widget.Toast;

import com.termux.victor.util.UUtils;
import com.google.gson.Gson;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.activity.BackNewActivity;
import com.termux.victor.dialog.MyDialog;
import com.termux.victor.fragment.BackupFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;



public class QZHFUtils {

    private File mFile = new File("/data/data/com.termux/");
    private File createFile;

    public void main(MyDialog myDialog, String systemName, BackupFragment restoreFragment) {

        new Thread(new Runnable() {
            @Override
            public void run() {


                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.getDialog_pro().setText(UUtils.getString(R.string.vs_start_testing_the_backup_environment));
                        myDialog.getDialog_pro_prog().setProgress(15);
                    }
                });

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.getDialog_pro().setText(UUtils.getString(R.string.vs_backup_environment_monitoring_completed));
                        myDialog.getDialog_pro_prog().setProgress(50);
                    }
                });

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.getDialog_pro().setText(UUtils.getString(R.string.vs_start_to_detect_whether_there_is_a_soft_sd));
                        myDialog.getDialog_pro_prog().setProgress(75);
                    }
                });


            /* TermuxApplication.mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("termux-setup-storage \n");
                    }
                });*/

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (!(new File("/data/data/com.termux/files/home/storage").exists())) {


                    UUtils.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(UUtils.getContext(), UUtils.getString(R.string.vs_the_storage_directory_was_not_found), Toast.LENGTH_SHORT).show();

                            myDialog.dismiss();
                            com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("termux-setup-storage");
                            restoreFragment.getActivity().finish();
                        }
                    });
                    return;
                }

                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.getDialog_pro().setText(UUtils.getString(R.string.vs_soft_connection_detected));
                        myDialog.getDialog_pro_prog().setProgress(80);
                    }
                });

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.getDialog_pro().setText(UUtils.getString(R.string.vs_start_backup_in_seconds_3));
                        myDialog.getDialog_pro_prog().setProgress(100);
                    }
                });


                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.dismiss();

                        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("cd ~ && cd ~ && tar -zcvf ./storage/shared/xinhao/data/" + systemName + " /data/data/com.termux/files && echo \"Backup complete, backup file is in -> internal storage/xinhao/data/\" \n");


                        restoreFragment.getActivity().finish();

                        BackNewActivity.mIsRun = false;
                    }
                });


            }
        }).start();

    }




}
