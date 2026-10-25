package com.zerosad.tvlauncher;

import android.service.notification.NotificationListenerService;

public class NotifService extends NotificationListenerService {
    public static volatile NotifService inst;

    @Override
    public void onListenerConnected() {
        inst = this;
    }

    @Override
    public void onListenerDisconnected() {
        if (inst == this) inst = null;
    }
}
