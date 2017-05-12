package com.kymjs.event.remote;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.kymjs.event.EventBus;


/**
 * Created by ZhangTao on 5/9/17.
 */

public class ChidoriServer extends Service {

    private final IChidoriServer.Stub mBinder = new IChidoriServer.Stub() {

        @Override
        public void onEvent(Bundle wrapper) throws RemoteException {
            EventBus.getDefault().post(wrapper.get(ChidoriClient.CHIDORI_EVENT));
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}