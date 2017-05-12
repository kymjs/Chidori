package com.kymjs.event.remote;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Created by ZhangTao on 5/9/17.
 */

public class ApplicationHolder extends ContentProvider {
    private static Context appContext;

    public synchronized static Context getAppContext() {
        if (appContext == null) {
            initContext();
        }
        return appContext;
    }

    @Override
    public boolean onCreate() {
        if (appContext == null) {
            synchronized (ApplicationHolder.class) {
                if (appContext == null) {
                    appContext = getContext();
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    private static void initContext() {
        try {
            appContext = (Context) Class.forName("android.app.AppGlobals").getMethod("getInitialApplication").invoke(null);
            if (appContext == null)
                throw new IllegalStateException("Static initialization of Applications must be on main thread.");
        } catch (final Exception e) {
            try {
                appContext = (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            } catch (final Exception ex) {
            }
        }
    }
}
