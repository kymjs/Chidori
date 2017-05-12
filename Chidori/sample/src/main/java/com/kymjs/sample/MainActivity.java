package com.kymjs.sample;

import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.kymjs.event.EventBus;

/**
 * Created by ZhangTao on 5/9/17.
 */

public class MainActivity extends AppCompatActivity {

    TextView mTextView;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EventBus.getDefault().post("hello");
            }
        });
        findViewById(R.id.button3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EventBus.getDefault().postRemote("com.kymjs.demo", "hello");
            }
        });
        mTextView = (TextView) findViewById(R.id.text);
        
        EventBus.getDefault().register(this);
    }

    @Keep
    public void onEvent(String action) {
        // 垃圾小米 rom 里把 append 方法给删了
        // mTextView.append("接收到消息：" + t + "\n");
        String str = mTextView.getText().toString();
        mTextView.setText(str + "接收到：" + action + "\n");
        
        Log.d("kymjs", "kymjs======sample======" + action);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
