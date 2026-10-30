package com.termux.victor.dialog;

import android.content.Context;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;

import com.termux.victor.util.UUtils;
import com.google.gson.Gson;
import com.termux.R;
import com.termux.victor.bean.MinLBean;
import com.termux.victor.utils.FileIOUtils;
import com.termux.victor.utils.SaveData;

import java.util.ArrayList;
import java.util.List;



/**
 * @author ZEL
 * @create By ZEL on 2020/10/20 17:05
 **/
public class MingLShowDialog extends BaseDialogCentre {
    public TextView start;
    public EditText edit_text;
    public EditText name_edit;
    public CardView mTitleCard;
    public CardView mSwitchCard;
    public TextView commit;
    public Switch switch_btn;
    public LinearLayout commit_ll;
    public LinearLayout def_commit_ll;
    public boolean isChecked = true;
    private AddCommitListener mAddCommitListener;
    public MingLShowDialog(@NonNull Context context) {
        super(context);
    }

    public MingLShowDialog(@NonNull Context context, int themeResId) {
        super(context, themeResId);
    }

    public void setAddCommitListener(AddCommitListener mAddCommitListener){
        this.mAddCommitListener = mAddCommitListener;
    }
    @Override
    void initViewDialog(View mView) {
        start = mView.findViewById(R.id.start);
        edit_text = mView.findViewById(R.id.edit_text);
        commit = mView.findViewById(R.id.commit);
        commit_ll = mView.findViewById(R.id.commit_ll);
        name_edit = mView.findViewById(R.id.name_edit);
        switch_btn = mView.findViewById(R.id.switch_btn);
        mTitleCard = mView.findViewById(R.id.title_card);
        mSwitchCard = mView.findViewById(R.id.switch_card);
        def_commit_ll = mView.findViewById(R.id.def_commit_ll);

        commit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        switch_btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                MingLShowDialog.this.isChecked = isChecked;
                UUtils.showLog("Auto enter: " + isChecked);
            }
        });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                String nameString = name_edit.getText().toString();
                String commitString = edit_text.getText().toString();

                if(nameString == null ||nameString.isEmpty()){
                    UUtils.showMsg(UUtils.getString(R.string.vs_name_is_required));
                    return ;
                }
                if(commitString == null ||commitString.isEmpty()){
                    UUtils.showMsg(UUtils.getString(R.string.vs_command_cannot_be_empty));
                    return ;
                }


                String commi22 = SaveData.getData("commi22");
                if(commi22 == null || commi22.isEmpty() || commi22.equals("def")){

                    MinLBean minLBean = new MinLBean();
                    MinLBean.Data data = new MinLBean.Data();
                    minLBean.data = data;

                    ArrayList<MinLBean.DataNum> arrayList = new ArrayList<>();
                    MinLBean.DataNum dataNum = new MinLBean.DataNum();
                    dataNum.id = System.currentTimeMillis();
                    dataNum.name = nameString;
                    dataNum.value = commitString;
                    dataNum.isChecked = isChecked;

                    arrayList.add(dataNum);
                    data.list = arrayList;

                    String s = new Gson().toJson(minLBean);
                    SaveData.saveData("commi22",s);
                    UUtils.showMsg(UUtils.getString(R.string.vs_added_successfully));
                    if(mAddCommitListener!= null){
                        mAddCommitListener.commit();
                    }
                    dismiss();
                }else{
                    try {
                        MinLBean minLBean = new Gson().fromJson(commi22, MinLBean.class);
                        List<MinLBean.DataNum> list = minLBean.data.list;
                        MinLBean.DataNum dataNum = new MinLBean.DataNum();
                        dataNum.id = System.currentTimeMillis();
                        dataNum.name = nameString;
                        dataNum.value = commitString;
                        dataNum.isChecked = isChecked;
                        list.add(0, dataNum);
                        String s = new Gson().toJson(minLBean);
                        SaveData.saveData("commi22", s);
                        if(mAddCommitListener!= null){
                            mAddCommitListener.commit();
                        }

                        UUtils.showLog("Add command 2: " + s);
                        UUtils.showMsg(UUtils.getString(R.string.vs_added_successfully));

                    }catch (Exception e){

                        TextShowDialog textShowDialog = new TextShowDialog(mContext);
                        textShowDialog.show();
                        textShowDialog.setCancelable(false);
                        textShowDialog.tishi_card_view.setVisibility(View.VISIBLE);
                        textShowDialog.name_edit.setText(UUtils.getString(R.string.vs_there_was_an_error_in_your_command));
                        textShowDialog.commit_ll.setVisibility(View.VISIBLE);
                        textShowDialog.edit_text.setText( SaveData.getData("commi22"));
                        textShowDialog.commit.setText(UUtils.getString(R.string.vs_empty));
                        textShowDialog.commit.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                SaveData.saveData("commi22","def");
                                UUtils.showMsg(UUtils.getString(R.string.vs_completed_empty));
                                textShowDialog.dismiss();
                            }
                        });
                        textShowDialog.diyige_ll.setVisibility(View.VISIBLE);
                        textShowDialog.cancel.setText(UUtils.getString(R.string.vs_cancel));
                        textShowDialog.cancel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                textShowDialog.dismiss();
                            }
                        });

                        textShowDialog.start.setText(UUtils.getString(R.string.vs_modify));
                        textShowDialog.start.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                SaveData.saveData("commi22",textShowDialog.edit_text.getText().toString());


                                UUtils.showMsg(UUtils.getString(R.string.vs_modified));
                                textShowDialog.dismiss();
                            }
                        });
                    }
                    dismiss();
                }
            }
        });

    }

    @Override
    int getContentView() {
        return R.layout.dialog_text_mingl;
    }

    public interface AddCommitListener{
        void commit();
    }
}
