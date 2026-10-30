package com.termux.victor.dialog;

import android.content.Context;
import android.os.Environment;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;

import com.termux.victor.util.UUtils;
import com.google.gson.Gson;
import com.termux.R;
import com.termux.victor.bean.MinLBean;
import com.termux.victor.utils.SaveData;

import java.io.File;


/**
 * @author ZEL
 * @create By ZEL on 2020/10/20 11:21
 **/
public class MinglingDaoruDaoChuDialog extends BaseDialogCentre {


    public CardView daoru;
    public CardView daochu;
    public File file ;
    public MinglingDaoruDaoChuDialog(@NonNull Context context) {
        super(context);
    }

    public MinglingDaoruDaoChuDialog(@NonNull Context context, int themeResId) {
        super(context, themeResId);
    }

    @Override
    void initViewDialog(View mView) {

        file  = new File(Environment.getExternalStorageDirectory(),"/xinhao/command/");
        if(!file.exists()){
            file.mkdir();
        }
        daoru = mView.findViewById(R.id.daoru);
        daochu = mView.findViewById(R.id.daochu);

        daoru.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                FileListConfigDialog fileListConfigDialog = new FileListConfigDialog(mContext);
                fileListConfigDialog.show();
                fileListConfigDialog.setOnItemFileClickListener(new FileListConfigDialog.OnItemFileClickListener() {
                    @Override
                    public void onItemClick(File file) {

                        UUtils.showLog("Output file: " + file.getAbsolutePath());

                        String fileString = UUtils.getFileString(file);

                        try {
                            new Gson().fromJson(fileString, MinLBean.class);
                            SaveData.saveData("commi22",fileString);
                            if(mSXXXXlistener!= null){
                                mSXXXXlistener.shuaxin();
                            }
                            UUtils.showMsg(UUtils.getString(R.string.vs_configuration_file_imported));
                            fileListConfigDialog.dismiss();
                            dismiss();
                        }catch (Exception e){
                            e.printStackTrace();
                            TextShowDialog textShowDialog = new TextShowDialog(mContext);
                            textShowDialog.show();
                            textShowDialog.edit_text.setText(e.toString());
                        }
                    }
                });

            }
        });

        daochu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String commi22 = SaveData.getData("commi22");
                if(commi22 == null || commi22.isEmpty() || commi22.equals("def")){
                    UUtils.showMsg(UUtils.getString(R.string.vs_no_data_currently));
                    return;
                }

                FileNameMingLDialog fileNameMingLDialog = new FileNameMingLDialog(mContext);
                fileNameMingLDialog.show();
                fileNameMingLDialog.setCancelable(false);
                fileNameMingLDialog.setOnSaveFileNameListener(new FileNameMingLDialog.OnSaveFileNameListener() {
                    @Override
                    public void saveFileName(String name) {

                        UUtils.setFileString(new File(file,"" + name + ".txt"),commi22);
                        fileNameMingLDialog.dismiss();
                        dismiss();
                        UUtils.showMsg(UUtils.getString(R.string.vs_saved_successfully));
                    }
                });


            }
        });

    }

    @Override
    int getContentView() {
        return R.layout.dialog_mingling_daoru_daochu;
    }


    private SXXXXlistener mSXXXXlistener;
    public void setSXXXXlistener(SXXXXlistener mSXXXXlistener){
        this.mSXXXXlistener = mSXXXXlistener;
    }

    public interface SXXXXlistener{

        void shuaxin();
    }

}
