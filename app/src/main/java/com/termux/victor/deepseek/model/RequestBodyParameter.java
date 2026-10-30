package com.termux.victor.deepseek.model;

import java.util.ArrayList;
import java.util.List;

public class RequestBodyParameter {
    public String model = "deepseek-chat";
    public List<RequestMessageItem> messages = null;
    public boolean stream = false;

    public RequestBodyParameter() {
    }

    public RequestBodyParameter(String model, List<RequestMessageItem> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }


    @Override
    public String toString() {
        return "{" +
                "\"model\":\"" + model + "\"," +
                "\"messages\":" + messages +","+
                "\"stream\":" + stream +
                '}';
    }

    public static void main(String[] args) {
        List<RequestMessageItem> requestMessageItemList = new ArrayList<>();
        requestMessageItemList.add(new RequestMessageItem("user", "What are you doing?"));
        requestMessageItemList.add(new RequestMessageItem("assistant", "Sleeping"));

        RequestBodyParameter requestBodyParameter1 = new RequestBodyParameter("deepseek-chat", requestMessageItemList, false);
        System.out.println(requestBodyParameter1);
    }
}
