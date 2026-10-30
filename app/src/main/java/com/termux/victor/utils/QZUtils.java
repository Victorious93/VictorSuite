package com.termux.victor.utils;

import android.util.Log;
import android.widget.Toast;

import com.termux.victor.util.UUtils;
import com.google.gson.Gson;
import com.termux.app.TermuxActivity;
import com.termux.victor.bean.CreateSystemBean;
import com.termux.victor.dialog.MyDialog;
import com.termux.victor.fragment.RestoreFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;



public class QZUtils {

    private File mFile = new File("/data/data/com.termux/");
    private File createFile;

    public void main(MyDialog myDialog, String systemName, File tarFle, RestoreFragment restoreFragment) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                Log.e("System: ", "run: " + tarFle.getName());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.getDialog_pro().setText("System search complete!");
                        myDialog.getDialog_pro_prog().setProgress(15);
                    }
                });

                //搜索系统


                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.getDialog_pro().setText("Creating new system disk!");
                        myDialog.getDialog_pro_prog().setProgress(25);
                    }
                });


             /*   UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("termux-setup-storage \n");
                    }
                });*/

                if (!(new File("/data/data/com.termux/files/home/storage").exists())) {


                    UUtils.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(UUtils.getContext(), "storage directory not found, please create it manually", Toast.LENGTH_SHORT).show();

                            myDialog.dismiss();
                            com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("termux-setup-storage");
                            restoreFragment.getActivity().finish();
                        }
                    });
                    return;
                }

                //创建新的系统盘
                createSystem(systemName);

                if (createFile == null) {
                    UUtils.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(UUtils.getContext(), "System disk creation failed, please use full manual restore", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                Log.e("System: ", "run: " + createFile.getAbsolutePath());

                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.getDialog_pro().setText("New system disk created!");
                        myDialog.getDialog_pro_prog().setProgress(45);
                    }
                });

                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                UUtils.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        myDialog.getDialog_pro().setText("Finishing up!");
                        myDialog.getDialog_pro_prog().setProgress(75);
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
                        myDialog.getDialog_pro().setText("Restore starts in 3 seconds!");
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
                        Toast.makeText(UUtils.getContext(), "Restoring..", Toast.LENGTH_SHORT).show();

                        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("echo \"----Manual restore started----\" \n");

                        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("cd ~ && cd ~ && tar -xzvf ./storage/shared/xinhao/data/" + tarFle.getName().replace(" ","") + "  -C ../../" + createFile.getName() + " && mv ../../" + createFile.getName() + "/data/data/com.termux/files/home ../../" + createFile.getName() +" && "+ "mv ../../" + createFile.getName() + "/data/data/com.termux/files/usr ../../" + createFile.getName()+" && rm -rf ../../"+createFile.getName()+"/data && echo \"System restore complete. Switch to your system in Container switching\" \n");
                        //com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("tar -xzvf./storage/shared/xinhao/data/" + tarFle.getName() + "  -C ../../" + createFile.getName() + " && mv ../../" + createFile.getName() + "/data/data/com.termux/files/home ../../" + createFile.getName() +" && "+ "mv ../../" + createFile.getName() + "/data/data/com.termux/files/usr ../../" + createFile.getName()+" && rm -rf ../../"+createFile.getName()+"/data && echo \"System restore complete. Switch to your system in Container switching\" \n");

                        try {
                            restoreFragment.getActivity().finish();
                        }catch (Exception e){
                            Toast.makeText(UUtils.getContext(), "A minor warning occurred; it can be ignored", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            }
        }).start();

    }


    //创建
    private void createSystem(String name) {
        //先扫描有多少文件
        File[] files = mFile.listFiles();

        if (files.length == 1) {
            //默认只有一个系统
            //直接创建
            createFile = new File(mFile, "files1");
            createFile.mkdirs();
            CreateSystemBean createSystemBean = new CreateSystemBean();
            createSystemBean.dir = createFile.getAbsolutePath();
            createSystemBean.systemName = name;

            String s = new Gson().toJson(createSystemBean);


            File fileInfo = new File(createFile, "/xinhao_system.infoJson");
            PrintWriter printWriter = null;
            try {

                fileInfo.createNewFile();
                printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileInfo)));

                printWriter.print(s);
                printWriter.flush();
                printWriter.close();

            } catch (IOException e) {
                Toast.makeText(UUtils.getContext(), "System creation failed! Please retry", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                return;
            } finally {
                if (printWriter != null) {
                    printWriter.close();
                }

            }


        } else {
            //有多个系统

            ArrayList<Integer> arrayList = new ArrayList<>();


            for (int i = 0; i < files.length; i++) {
                if (files[i].getName().startsWith("files")) {
                    // Log.e("XINHAO_HAN", "readFile: " + files[i].getAbsolutePath());
                    String name1 = files[i].getName();
                    String substring = name1.substring(5, name1.length());

                    if (substring.isEmpty()) {
                        arrayList.add(0);
                    } else {
                        arrayList.add(Integer.parseInt(substring));
                    }

                }
            }

            // Log.e("XINHAO_HAN", "createSystem: " + arrayList);


            int max = getMax(arrayList);
            Log.e("XINHAO_HAN", "Max: " + max);


            //直接创建
            createFile = new File(mFile, "files" + (max + 1));
            createFile.mkdirs();
            CreateSystemBean createSystemBean = new CreateSystemBean();
            createSystemBean.dir = createFile.getAbsolutePath();
            createSystemBean.systemName = name;

            String s = new Gson().toJson(createSystemBean);


            File fileInfo = new File(createFile, "/xinhao_system.infoJson");
            PrintWriter printWriter = null;
            try {

                fileInfo.createNewFile();
                printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileInfo)));

                printWriter.print(s);
                printWriter.flush();
                printWriter.close();

            } catch (IOException e) {
                Toast.makeText(UUtils.getContext(), "System creation failed! Please retry", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                return;
            } finally {
                if (printWriter != null) {
                    printWriter.close();
                }

            }
        }


    }


    //比大小
    private int getMax(ArrayList<Integer> number) {

        int temp = number.get(0);

        for (int i = 0; i < number.size(); i++) {

            if (number.get(i) > temp) {
                temp = number.get(i);
            }

        }


        return temp;
    }
}
