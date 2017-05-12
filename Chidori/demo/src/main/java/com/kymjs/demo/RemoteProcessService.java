package com.kymjs.demo;

import com.kymjs.event.remote.ChidoriServer;

/**
 * 工作在远端进程的接受者，要接收到消息必须在manifest中注册一个Service，这个Service继承自ChidoriServer，
 * 且进程与本接收者进程相同
 * <p>
 * <p>
 * <p>
 * Created by ZhangTao on 5/12/17.
 */
public class RemoteProcessService extends ChidoriServer {
}
