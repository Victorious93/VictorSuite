package com.termux.victor.activity;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.victor.util.UUtils;
import com.google.gson.Gson;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxInstaller;
import com.termux.app.TermuxService;
import com.termux.victor.activity.adapter.CreateSystemAdapter;
import com.termux.victor.bean.CreateSystemBean;
import com.termux.victor.bean.ReadSystemBean;
import com.termux.victor.dialog.MyDialog;
import com.termux.victor.shell.ExeCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;


public class SwitchActivity extends AppCompatActivity  implements View.OnClickListener {


    private ListView list;

    private ImageView create_img;

    private File mFile = new File("/data/data/com.termux/");
    private File mFileTEMP = new File("/data/data/com.termux/temp");
    private File mDefFile = new File("/data/data/com.termux/files/xinhao_system.infoJson");
    private File mFileHomeStatic = new File("/data/data/com.termux/busybox_static");
    private File mFileHome = new File("/data/data/com.termux/busybox");
    private static final String ACTION_STOP_SERVICE = "com.termux.service_stop";
    private CreateSystemAdapter createSystemAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_switch);

        list = findViewById(R.id.list_switch);

        create_img = findViewById(R.id.create_img);

        create_img.setOnClickListener(this);

        clickList();
        isIofo();

        readFile();
        //  testJson();


    }


    //判断默认系统
    private void isIofo() {


        if (!mDefFile.exists()) {

            try {
                mDefFile.createNewFile();

                CreateSystemBean createSystemBean = new CreateSystemBean();

                createSystemBean.systemName = UUtils.getString(R.string.vs_unnamedcontainer);

                createSystemBean.dir = "/data/data/com.termux/files";

                String s = new Gson().toJson(createSystemBean);

                PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(mDefFile)));

                printWriter.print(s);

                printWriter.flush();

                printWriter.close();


            } catch (IOException e) {
                e.printStackTrace();
            }


        }


    }

    //点击事件

    private void clickList() {

        // Toast.makeText(this, "执行了", Toast.LENGTH_SHORT).show();

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                List<ReadSystemBean> mList = createSystemAdapter.mList;


                // Toast.makeText(SwitchActivity.this, "" + position, Toast.LENGTH_SHORT).show();

                //当前系统


                String[] strings = {UUtils.getString(R.string.vs_delete), UUtils.getString(R.string.vs_switch)};

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog
                    .Builder(SwitchActivity.this);
                builder.setTitle(UUtils.getString(R.string.vs_delete_complete_need_to_re_enter_to_refr));
                // builder.setMessage("这是个滚动列表，往下滑");
                builder.setItems(strings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Toast.makeText(TermuxActivity.this, "选择了第" + which + "个", Toast.LENGTH_SHORT).show();

                        if (which == 0) {

                            builder.create().dismiss();
                            AlertDialog.Builder a = new AlertDialog.Builder(SwitchActivity.this);
                            a.setTitle("Are you sure you want to delete?");
                            a.setMessage("Make sure storage permission is NOT granted!\nOtherwise this may wipe everything on your internal storage!\nAre you sure you want to delete this system?\nBe careful: deleted data cannot be recovered!");
                            a.setNegativeButton("I'm sure", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    a.create().dismiss();

                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (mList.get(position).dir.equals("/data/data/com.termux/files")) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(SwitchActivity.this, "You cannot delete the main system", Toast.LENGTH_SHORT).show();

                                                    }
                                                });
                                                return;
                                            }

                                            Log.e("XINHAO_HAN", "Delete system: " + mList.get(position).dir);

                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    MyDialog myDialog = new MyDialog(SwitchActivity.this);
                                                    myDialog.show();
                                                    myDialog.getDialog_title().setText("Deleting, please wait...");
                                                    myDialog.getDialog_pro().setText("Duration depends on the size of your system");
                                                }
                                            });
                                            Log.e("XINHAO_HAN", "Delete directory: " + mList.get(position).dir);

                                            String cpu = TermuxInstaller.determineTermuxArchName();

                                            switch (cpu) {
                                                case "aarch64":
                                                    writerFile("arm_64/busybox", mFileHome, 1024);
                                                    writerFile("arm_64/busybox_static", mFileHomeStatic, 1024);
                                                    // writerFile("arm_64/proot", mFileHomeProot, 1024);
                                                    break;
                                                case "arm":
                                                    writerFile("arm/busybox", mFileHome, 1024);
                                                    // writerFile("arm/busybox_static", mFileHomeStatic, 1024);
                                                    //   writerFile("arm/proot", mFileHomeProot, 1024);
                                                    break;
                                                case "x86_64":
                                                    writerFile("x86/busybox", mFileHome, 1024);
                                                    //  writerFile("x86/busybox_static", mFileHomeStatic, 1024);
                                                    //  writerFile("x86/proot", mFileHomeProot, 1024);
                                                    break;
                                            }

                                            try {
                                                Runtime.getRuntime().exec("chmod 777 " + mFileHome.getAbsolutePath());
                                                Runtime.getRuntime().exec("chmod 777 " + mFileHomeStatic.getAbsolutePath());
                                                //Runtime.getRuntime().exec("chmod 777 " + mFileHomeProot.getAbsolutePath());
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                            ExeCommand cmd2 = new ExeCommand(false).run(mFileHome.getAbsolutePath() + " rm -rf " + mList.get(position).dir, 60000,false);

                                            while (cmd2.isRunning()) {
                                                try {
                                                    Thread.sleep(5);
                                                } catch (Exception e) {

                                                }
                                                String buf = cmd2.getResult();
                                                //do something}

                                                Log.e("XINHAO_HAN", "run: " + buf);
                                            }


                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {

                                                    if(new File(mList.get(position).dir).exists()){
                                                        Toast.makeText(SwitchActivity.this, "Clearing leftover system files", Toast.LENGTH_SHORT).show();
                                                        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("chmod 777 -R "+mList.get(position).dir+"&& rm -rf " + mList.get(position).dir + " \n");
                                                        finish();
                                                    }else{
                                                        Toast.makeText(SwitchActivity.this, "Deleted!", Toast.LENGTH_SHORT).show();
                                                        finish();
                                                    }


                                                }
                                            });

                                        }
                                    }).start();

                                }
                            });
                            a.setPositiveButton("Keep it", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    a.create().dismiss();
                                    Toast.makeText(SwitchActivity.this, "Operation ignored", Toast.LENGTH_SHORT).show();
                                }
                            });
                            //setNegativeButton
                            a.setNeutralButton("Don't delete", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    a.create().dismiss();
                                    Toast.makeText(SwitchActivity.this, "Operation ignored", Toast.LENGTH_SHORT).show();
                                }
                            });
                            a.show();


                        } else {

                            try {


                                //本目录系统
                                String temp;

                                String tempStr = "";
                                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(mDefFile)));

                                while ((temp = bufferedReader.readLine()) != null) {
                                    tempStr += temp;
                                }

                                //要被替换的系统
                                ReadSystemBean readSystemBean = mList.get(position);
                                //本目录的系统
                                CreateSystemBean readSystemBean1 = new Gson().fromJson(tempStr, CreateSystemBean.class);

                                //要被替换的
                                String path = readSystemBean.dir;
                                //  Log.e("XINHAO_HAN", "要被替换的: " + path);
                                //本目录的
                                String pathThis = readSystemBean1.dir;
                                // Log.e("XINHAO_HAN", "本目录的: " + pathThis);


                                File filePath = new File(path);

                                File filePathThis = new File(pathThis);


                                File fileOutPath = new File(filePath, "/xinhao_system.infoJson");
                                // /data/data/com.termux/files1/xinhao_system.infoJson

                                File fileOutPathThis = new File(filePathThis, "/xinhao_system.infoJson");
                                // /data/data/com.termux/files/xinhao_system.infoJson

                                try {
                                    fileOutPathThis.delete();

                                    fileOutPathThis.createNewFile();

                                    readSystemBean1.dir = readSystemBean.dir;

                                    PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileOutPathThis)));
                                    printWriter.print(new Gson().toJson(readSystemBean1));
                                    Log.e("XINHAO_HAN", "Write json: " + new Gson().toJson(readSystemBean1));
                                    printWriter.flush();

                                    printWriter.close();


                                } catch (IOException e) {
                                    e.printStackTrace();
                                }


                                try {
                                    fileOutPath.delete();

                                    fileOutPath.createNewFile();

                                    readSystemBean1.dir = "/data/data/com.termux/files";
                                    readSystemBean1.systemName = readSystemBean.name;
                                    // readSystemBean1.systemName = readSystemBean.name;
                                    // readSystemBean1.systemName = readSystemBean1.systemName;
                                    //  Log.e("XINHAO_HAN", "本目录的: " + pathThis);

                                    PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileOutPath)));


                                    printWriter.print(new Gson().toJson(readSystemBean1));

                                    printWriter.flush();

                                    printWriter.close();


                                } catch (IOException e) {
                                    e.printStackTrace();
                                }


                                filePath.renameTo(mFileTEMP);
                                filePathThis.renameTo(filePath);
                                mFileTEMP.renameTo(filePathThis);


                            } catch (Exception e) {
                                Toast.makeText(SwitchActivity.this, "Read failed!", Toast.LENGTH_SHORT).show();
                            }


                            setAllFlase(mList);

                            mList.get(position).isCkeck = true;

                            createSystemAdapter.notifyDataSetChanged();

                            AlertDialog.Builder ab = new AlertDialog.Builder(SwitchActivity.this);

                            ab.setTitle("Notice");

                            ab.setCancelable(false);

                            ab.setMessage("Switched!\nRestart the app to apply.\nPlease restart it manually:\nopen the recent-apps screen (usually the '□' button) and swipe the app away. ");

                            ab.setNegativeButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(SwitchActivity.this, "Please exit the app manually", Toast.LENGTH_SHORT).show();
                                    new Intent(SwitchActivity.this, TermuxService.class).setAction(ACTION_STOP_SERVICE);
                                    System.exit(0);
                                    finish();
                                }
                            });

                            ab.setPositiveButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(SwitchActivity.this, "Setting applied! Please restart the app", Toast.LENGTH_SHORT).show();
                                    finish();

                                }
                            });

                            ab.show();

                        }

                    }
                });
                builder.show();
                //readSystemBean.dir


            }
        });


    }

    //全部flase

    private void setAllFlase(List<ReadSystemBean> mList) {

        for (int i = 0; i < mList.size(); i++) {

            mList.get(i).isCkeck = false;
        }
        createSystemAdapter.notifyDataSetChanged();


    }


    //全部ture


    //先读取
    private void readFile() {

        File[] files = mFile.listFiles();

        ArrayList<ReadSystemBean> arrayList = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {


            if (files[i].getName().startsWith("files")) {

                ReadSystemBean readSystemBean = new ReadSystemBean();

                readSystemBean.dir = files[i].getAbsolutePath();

                String name = readInfo(files[i].getAbsolutePath());

                if (name == null) {
                    new File(files[i],"/xinhao_system.infoJson").delete();

                    Toast.makeText(this, "Config file is corrupted; reopen to recreate it (retry until it opens)", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                readSystemBean.name = name;
                arrayList.add(readSystemBean);

            }

        }

        Log.e("XINHAO_HAN", "readFile: " + arrayList);
        setAdapter(arrayList);
    }

    //开始设置

    private void setAdapter(ArrayList<ReadSystemBean> arrayList) {

        createSystemAdapter = new CreateSystemAdapter(arrayList, this);

        list.setAdapter(createSystemAdapter);

        setDefSystem(arrayList);
    }
    //设置默认

    private void setDefSystem(ArrayList<ReadSystemBean> arrayList) {

        //取本地的系统目录
        try {
            File file = new File("/data/data/com.termux/files/xinhao_system.infoJson");

            if (!file.exists()) {
                Toast.makeText(this, "Your current default system has no config file", Toast.LENGTH_SHORT).show();
                return;
            }
            String temp;

            String tempStr = "";
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

            while ((temp = bufferedReader.readLine()) != null) {
                tempStr += temp;
            }


            CreateSystemBean createSystemBean = new Gson().fromJson(tempStr, CreateSystemBean.class);

            if(createSystemBean == null){
                createSystemBean = new CreateSystemBean();
                createSystemBean.dir = "/data/data/com.termux/files/";
                createSystemBean.systemName = "Default system";
            }


            for (int i = 0; i < arrayList.size(); i++) {


                if (arrayList.get(i).name.equals(createSystemBean.systemName)) {

                    arrayList.get(i).isCkeck = true;

                    break;
                }


            }

            createSystemAdapter.notifyDataSetChanged();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    //读取
    private String readInfo(String path) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(new File(path), "/xinhao_system.infoJson"))));
            String temp = "";
            String tempSystem = "";
            while ((temp = bufferedReader.readLine()) != null) {
                tempSystem += temp;
            }
            bufferedReader.close();

            Log.e("XINHAO_HAN", "readInfo: " + tempSystem);
            CreateSystemBean createSystemBean = new Gson().fromJson(tempSystem, CreateSystemBean.class);


            if(createSystemBean == null){
                return"Broken system";
            }else{
                return createSystemBean.systemName;
            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        return "Default system";
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.create_img) {
            createSystemDialog();
        }
    }

    //创建新的系统
    private void createSystemDialog() {

        final EditText et = new EditText(this);
        new AlertDialog.Builder(this).setTitle("Enter a new Linux name")
            .setIcon(R.mipmap.linux_new_ico)
            .setView(et)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    //按下确定键后的事件
                    createSystem(et.getText().toString());
                }
            }).setNegativeButton("Cancel", null).show();
    }

    //创建
    private void createSystem(String name) {
        //先扫描有多少文件
        File[] files = mFile.listFiles();

        if (files.length == 1) {
            //默认只有一个系统
            //直接创建
            File createFile = new File(mFile, "files1");
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
                Toast.makeText(this, "System creation failed! Please retry", Toast.LENGTH_SHORT).show();
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
            File createFile = new File(mFile, "files" + (max + 1));
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
                Toast.makeText(this, "System creation failed! Please retry", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                return;
            } finally {
                if (printWriter != null) {
                    printWriter.close();
                }

            }
        }


        readFile();
        createSystemAdapter.notifyDataSetChanged();
    }

    private void createSystem() {

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


    private void writerFile(String name, File mFile, int size) {

        try {
            InputStream open = this.getAssets().open(name);

            int len = 0;

            byte[] b = new byte[size];

            if (!mFile.exists()) {
                mFile.createNewFile();
            }

            FileOutputStream fileOutputStream = new FileOutputStream(mFile);

            while ((len = open.read(b)) != -1) {
                fileOutputStream.write(b, 0, len);
            }

            fileOutputStream.flush();
            open.close();
            fileOutputStream.close();
        } catch (Exception e) {

            Log.e("XINHAO_HAN_FILE ", "writerFile: " + e.toString());
        }

    }


}
