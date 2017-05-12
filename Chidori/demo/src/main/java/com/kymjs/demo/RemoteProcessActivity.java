package com.kymjs.demo;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.kymjs.event.EventBus;

/**
 * 工作在远端进程的接受者，要接收到消息必须在manifest中注册一个Service，这个Service继承自ChidoriServer，
 * 且进程与本接收者进程相同
 * <p>
 * <p>
 * <p>
 * Created by ZhangTao on 5/12/17.
 */
public class RemoteProcessActivity extends AppCompatActivity {

    TextView mTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RemoteProcessActivity.this, MainActivity.class));
            }
        });

        mTextView = (TextView) findViewById(R.id.text);
        EventBus.getDefault().register(this);
    }

    @Keep
    public void onEvent(String action) {
        mTextView.setText("远端进程接收到：" + action);

        Log.d("kymjs", "kymjs======demo=remote=====" + action);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
