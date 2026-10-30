package com.termux.victor.deepseek;


import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.termux.R;
import com.termux.victor.deepseek.data.ChatDatabaseHelper;
import com.termux.victor.deepseek.model.AiClient;
import com.termux.victor.deepseek.model.Config;
import com.termux.victor.deepseek.model.DeepSeekClient;
import com.termux.victor.deepseek.model.RequestMessageItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestActivity extends AppCompatActivity {
    private EditText messageInput;
    private Button sendButton;
    private ChatDatabaseHelper dbHelper;


    private AiClient aiClient = new AiClient();
    private List<RequestMessageItem> requestMessageItemList = new ArrayList<>();

    private TextView testText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        dbHelper = new ChatDatabaseHelper(this);


        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        testText = findViewById(R.id.testText);


        sendButton.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String message = messageInput.getText().toString();
        if (!message.isEmpty()) {
            testText.setText("");
            sendButton.setText("Answering...");
            sendButton.setEnabled(false);

            reqModel(message);
        }
    }

    private void reqModel(String text){
        requestMessageItemList.add(new RequestMessageItem("user", text));

        aiClient.ask(requestMessageItemList, true, new DeepSeekClient.Lis() {
            @Override
            public void error() {
                System.out.println("\nFailed (server response timed out)");
                input();
            }

            @Override
            public void msg(String msg, boolean isError) {
                System.out.print(aiClient.getMsg(msg));
                runOnUiThread(()->{
                    testText.append(aiClient.getMsg(msg));
                });
            }

            @Override
            public void end() {
                System.out.print("\n");
                input();
            }
        });
    }

    private void input(){
        runOnUiThread(()->{
            sendButton.setText("Send");
            sendButton.setEnabled(true);
        });
    }
}
