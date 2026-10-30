package com.termux.victor.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Environment;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.victor.util.UUtils;
import com.github.mjdev.libaums.fs.UsbFile;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxInstaller;
import com.termux.app.TermuxService;
import com.termux.shared.termux.TermuxConstants;
import com.termux.victor.activity.BackNewActivity;
import com.termux.victor.activity.adapter.RestoreAdapter;
import com.termux.victor.data.UsbFileData;
import com.termux.victor.dialog.MyDialog;
import com.termux.victor.dialog.YesNoDialog;
import com.termux.victor.shell.ExeCommand;
import com.termux.victor.utils.QZUtils;
import com.termux.victor.utils.SaveData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;



public class RestoreFragment extends BaseFragment {

    private File mFile12 = new File("/data/data/com.termux/files/usr");
    private File mFile13 = new File("/data/data/com.termux/files/home");
    private File mSdFile = new File(Environment.getExternalStorageDirectory(), "/xinhao/data/");
    private File mFileHomeFiles = new File("/data/data/com.termux/files/");
    private File mFileHome = new File("/data/data/com.termux/busybox");
    private File mFileHomeStatic = new File("/data/data/com.termux/busybox_static");
    private File mFileHomeProot = new File("/data/data/com.termux/proot");
    private File mFileHomeMain = new File("/data/data/com.termux/files/usr/bin/tar");
    private File mFileHomeMainTar = new File("/data/data/com.termux/busybox_tar");
    private ListView mListView;
    private TextView mTitle;
    private TextView mStartRe;
    private Process mProcess;
    private RestoreAdapter restoreAdapter;

    private ArrayList<File> files = new ArrayList<>();

    @Override
    public View getFragmentView() {
        return View.inflate(getContext(), R.layout.fragment_restore_k, null);
    }

    @Override
    public void initFragmentView(View mView) {

        mListView = (ListView) findViewById(R.id.list_view);
        mTitle = (TextView) findViewById(R.id.title);
        mStartRe = (TextView) findViewById(R.id.start_re);



        File[] files1 = mSdFile.listFiles();

        if (files1 == null) {
            mStartRe.setText(UUtils.getString(R.string.vs_no_recoverable_files_sd));
            return;
        }

        files.clear();

        for (int i = 0; i < files1.length; i++) {

            if(files1[i].getAbsolutePath().endsWith("tar.gz")){
                files.add(files1[i]);
            }



        }

        if (files.size() == 0) {
            mStartRe.setText(UUtils.getString(R.string.vs_no_recoverable_files_sd));
        }


        restoreAdapter = new RestoreAdapter(files);
        mListView.setAdapter(restoreAdapter);



        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            //标记恢复处.....


            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File file = files.get(position);


                YesNoDialog yesNoDialog = new YesNoDialog(getActivity());

                yesNoDialog.getTitleTv().setText(UUtils.getString(R.string.vs_recovery_method));
                yesNoDialog.getMsgTv().setText(UUtils.getString(R.string.vs_determine_to_restore_and_create_a_new_co));

                yesNoDialog.getYesTv().setText(UUtils.getString(R.string.vs_determine_to_restore_and_create_a_new_co));
                yesNoDialog.getYesTv().setVisibility(View.GONE);
                yesNoDialog.setCancelable(true);

                yesNoDialog.getNoTv().setText(UUtils.getString(R.string.vs_restore));
                yesNoDialog.getNoTv().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {



                        if (!(new File("/data/data/com.termux/files/home/storage").exists())){

                            Toast.makeText(UUtils.getContext(), UUtils.getString(R.string.vs_no_directory_found), Toast.LENGTH_SHORT).show();

                            UUtils.getHandler().post(new Runnable() {
                                @Override
                                public void run() {


                                    com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(UUtils.getString(R.string.vs_echo_input_enter));

                                    com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal("termux-setup-storage ");
                                    getActivity().finish();
                                }
                            });



                            return;
                        }



                        if(yesNoDialog.getInputSystemName().getText().toString().isEmpty()){
                            yesNoDialog.getInputSystemName().setVisibility(View.VISIBLE);

                            Toast.makeText(getContext(), UUtils.getString(R.string.vs_please_enter_the_system_name), Toast.LENGTH_SHORT).show();
                            return;
                        }


                        yesNoDialog.dismiss();

                        MyDialog myDialog = new MyDialog(getActivity());

                        myDialog.getDialog_title().setText(UUtils.getString(R.string.vs_please_wait_for_this_process_to_prohibit));

                        myDialog.getDialog_pro().setText(UUtils.getString(R.string.vs_searching_for_multiple_systems));
                        myDialog.getDialog_pro_prog().setVisibility(View.VISIBLE);
                        myDialog.getDialog_pro().setVisibility(View.VISIBLE);
                        myDialog.getDialog_pro_prog().setMax(100);
                        myDialog.getDialog_pro_prog().setProgress(6);

                        myDialog.show();

                        new QZUtils().main(myDialog,yesNoDialog.getInputSystemName().getText().toString(),file,RestoreFragment.this);


                    }
                });

                yesNoDialog.show();

                yesNoDialog.getTitleTv().setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            yesNoDialog.dismiss();
                        }
                    }
                );


                yesNoDialog.getYesTv().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        yesNoDialog.dismiss();

                        AlertDialog.Builder ab = new AlertDialog.Builder(getActivity());

                        ab.setTitle(UUtils.getString(R.string.vs_danger));

                        ab.setMessage(UUtils.getString(R.string.vs_restoring_device_data_will_erase_all_pre));

                        ab.setPositiveButton(UUtils.getString(R.string.vs_i_know_what_i_m_doing), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                mListView.setVisibility(View.GONE);
                                mTitle.setVisibility(View.VISIBLE);
                                Toast.makeText(getContext(), UUtils.getString(R.string.vs_begin), Toast.LENGTH_SHORT).show();
                                ab.create().dismiss();
                                BackNewActivity.mIsRun = true;


                            }
                        });

                        ab.setNeutralButton(UUtils.getString(R.string.vs_i_m_considering), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ab.create().dismiss();
                            }
                        });
                        ab.show();

                    }
                });


            }
        });


    }







    //写出文件
    private void writerFile(String name, File mFile) {

        try {
            InputStream open = getContext().getAssets().open(name);

            int len = 0;

            if (!mFile.exists()) {
                mFile.createNewFile();
            }

            FileOutputStream fileOutputStream = new FileOutputStream(mFile);

            while ((len = open.read()) != -1) {
                fileOutputStream.write(len);
            }

            fileOutputStream.flush();
            open.close();
            fileOutputStream.close();
        } catch (Exception e) {

        }

    }

    private void writerFile(String name, File mFile, int size) {

        try {
            InputStream open = getContext().getAssets().open(name);

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
