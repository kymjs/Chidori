package com.kymjs.event.remote;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * Created by ZhangTao on 5/9/17.
 */

public class ChidoriClient extends BroadcastReceiver {

    public static final String CHIDORI_ACTION = "com.kymjs.event.EventBus.Chidori";
    public static final String CHIDORI_EVENT = "com.kymjs.event.EventBus.event";
    public static final String CHIDORI_FILTER = "com.kymjs.event.EventBus.filter";
    public static final String CHIDORI_WRAPPER_DATA = "chidori_intent";
    public static final String CHIDORI_SERVER = "com.kymjs.event.EventBus.serverClass";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        //check
        if (!CHIDORI_ACTION.equals(intent.getAction())) {
            return;
        }
        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                IChidoriServer server = IChidoriServer.Stub.asInterface(service);

                if (server != null) {
                    if (context.getPackageName().equalsIgnoreCase(intent.getStringExtra(CHIDORI_FILTER))) {
                        try {
                            server.onEvent(intent.getBundleExtra(CHIDORI_WRAPPER_DATA));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        Class<?>[] serverClazzList = (Class<?>[]) intent.getSerializableExtra(CHIDORI_SERVER);
        if (serverClazzList == null) {
            serverClazzList = new Class<?>[]{
                    ChidoriServer.class,
            };
        }
        Log.d("kymjs", "=========" + serverClazzList.getClass().getName());

        for (Class clazz : serverClazzList) {
            Intent connectIntent = new Intent(ApplicationHolder.getAppContext(), clazz);
            ApplicationHolder.getAppContext().bindService(connectIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        disconnect(serviceConnection);
    }

    private static final int FIVE_MINUTES = 1000 * 60 * 5;

    private void disconnect(final ServiceConnection serviceConnection) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (ChidoriClient.this) {
                    try {
                        if (ApplicationHolder.getAppContext() != null && serviceConnection != null) {
                            ApplicationHolder.getAppContext().unbindService(serviceConnection);
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }, FIVE_MINUTES);
    }

}
