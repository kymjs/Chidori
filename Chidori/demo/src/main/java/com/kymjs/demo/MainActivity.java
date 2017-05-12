package com.kymjs.demo;

import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.kymjs.event.EventBus;

public class MainActivity extends AppCompatActivity {

    TextView mTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button button1 = (Button) findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = random();
                EventBus.getDefault().post("hello" + i);
                button1.setText("发送消息：" + "hello" + i);
            }
        });
        final Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //发送给非主进程时必须先连接服务端
                EventBus.getDefault().connect(RemoteProcessService.class);
                //其实还可以指定多个
                //EventBus.getDefault().connect(RemoteProcessService.class，ChidoriServer.class);
                int r = random();
                EventBus.getDefault().postRemote("com.kymjs.demo", "hello" + r);
                button2.setText("发送消息：" + "hello" + r);

            }
        });

        final Button button3 = (Button) findViewById(R.id.button3);
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int r = random();
                EventBus.getDefault().postRemote("com.kymjs.sample", "hello" + r);
                button3.setText("发送消息：" + "hello" + r);
            }
        });

        mTextView = (TextView) findViewById(R.id.text);
        EventBus.getDefault().register(this);
    }

    @Keep
    public void onEvent(String action) {
        // 垃圾小米 rom 里把 append 方法给删了
        // mTextView.append("接收到消息：" + t + "\n");

        mTextView.setText(null);
        String str = mTextView.getText().toString();
        mTextView.setText(str + "接收到：" + action + "\n");

        Log.d("kymjs", "kymjs=======demo=====" + action);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    public int random() {
        return (int) (Math.random() * 10);
    }
}
