package com.zerosad.tvlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentUris;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.inputmethod.InputMethodManager;
import android.graphics.Matrix;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView web;
    private final Handler handler = new Handler();
    private final Runnable reloadTask = new Runnable() {
        public void run() {
            if (web != null) web.reload();
        }
    };
    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            handler.removeCallbacks(reloadTask);
            handler.postDelayed(reloadTask, 900);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        web.requestFocus();
        web.loadUrl("file:///android_asset/index.html");
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(pkgReceiver, f);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        web.evaluateJavascript("window.goHome&&goHome()", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.requestFocus();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        web.evaluateJavascript("window.onPhotoPerm&&onPhotoPerm()", null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            web.evaluateJavascript(
                "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));",
                null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private class Bridge {
        private final Map<String, ResolveInfo> found = new HashMap<>();

        private void scan(String category) {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(category);
            List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
            for (ResolveInfo ri : list) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName())) continue;
                if (!found.containsKey(p)) found.put(p, ri);
            }
        }

        @JavascriptInterface
        public String getApps() {
            try {
                found.clear();
                scan(Intent.CATEGORY_LEANBACK_LAUNCHER);
                scan(Intent.CATEGORY_LAUNCHER);
                final PackageManager pm = getPackageManager();
                List<ResolveInfo> all = new ArrayList<>(found.values());
                Collections.sort(all, new Comparator<ResolveInfo>() {
                    public int compare(ResolveInfo a, ResolveInfo c) {
                        return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(c.loadLabel(pm)));
                    }
                });
                JSONArray arr = new JSONArray();
                for (ResolveInfo ri : all) {
                    JSONObject o = new JSONObject();
                    o.put("n", String.valueOf(ri.loadLabel(pm)));
                    o.put("p", ri.activityInfo.packageName);
                    o.put("b", ri.activityInfo.loadBanner(pm) != null);
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String getImage(String pkg) {
            try {
                ResolveInfo ri = found.get(pkg);
                if (ri == null) return "";
                PackageManager pm = getPackageManager();
                Drawable d = ri.activityInfo.loadBanner(pm);
                int w = 320, h = 180;
                if (d == null) {
                    d = ri.loadIcon(pm);
                    w = 128;
                    h = 128;
                }
                Bitmap bmp;
                if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                    bmp = Bitmap.createScaledBitmap(((BitmapDrawable) d).getBitmap(), w, h, true);
                } else {
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bmp);
                    d.setBounds(0, 0, w, h);
                    d.draw(c);
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean launch(final String pkg) {
            final PackageManager pm = getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final Intent go = i;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        startActivity(go);
                    } catch (Exception ignored) {
                    }
                }
            });
            return true;
        }

        private Intent newTask(Intent i) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        }

        @JavascriptInterface
        public boolean openUrl(String url) {
            try {
                startActivity(newTask(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String openTvSettings() {
            try {
                startActivity(newTask(new Intent(Settings.ACTION_SETTINGS)));
                return "ok";
            } catch (Exception e) {
                return "err";
            }
        }

        @JavascriptInterface
        public boolean hasNotifAccess() {
            try {
                String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
                return s != null && s.contains(getPackageName());
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String openNotifAccess() {
            try {
                startActivity(newTask(new Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS")));
                return "ok";
            } catch (Exception e) {
                return "err";
            }
        }

        @JavascriptInterface
        public void showKeyboard() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        web.requestFocus();
                        InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        m.showSoftInput(web, 0);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public String uninstall(String pkg) {
            try {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return "system";
                startActivity(newTask(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))));
                return "ok";
            } catch (Exception e) {
                return "err";
            }
        }

        private MediaController activeController() {
            try {
                MediaSessionManager m = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
                List<MediaController> l = m.getActiveSessions(new ComponentName(MainActivity.this, NotifService.class));
                MediaController first = null;
                for (MediaController c : l) {
                    if (c.getMetadata() == null) continue;
                    PlaybackState st = c.getPlaybackState();
                    if (st != null && st.getState() == PlaybackState.STATE_PLAYING) return c;
                    if (first == null) first = c;
                }
                return first;
            } catch (Exception e) {
                return null;
            }
        }

        private String appLabel(String pkg) {
            try {
                PackageManager pm = getPackageManager();
                return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (Exception e) {
                return pkg;
            }
        }

        @JavascriptInterface
        public String getNowPlaying() {
            JSONObject root = new JSONObject();
            try {
                boolean perm = hasNotifAccess();
                root.put("perm", perm);
                if (perm) {
                    MediaController c = activeController();
                    if (c != null) {
                        MediaMetadata md = c.getMetadata();
                        JSONObject m = new JSONObject();
                        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                        String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                        if (a == null) a = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                        PlaybackState st = c.getPlaybackState();
                        m.put("t", t == null ? "" : t);
                        m.put("a", a == null ? "" : a);
                        m.put("app", appLabel(c.getPackageName()));
                        m.put("p", st != null && st.getState() == PlaybackState.STATE_PLAYING);
                        m.put("k", c.getPackageName() + "|" + t + "|" + a);
                        root.put("m", m);
                    }
                    JSONArray n = new JSONArray();
                    NotifService svc = NotifService.inst;
                    if (svc != null) {
                        StatusBarNotification[] list = svc.getActiveNotifications();
                        if (list != null) {
                            for (StatusBarNotification sbn : list) {
                                if (n.length() >= 3) break;
                                Notification nt = sbn.getNotification();
                                if (nt == null || sbn.getPackageName().equals(getPackageName())) continue;
                                if ((nt.flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;
                                if ((nt.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                                if (Notification.CATEGORY_TRANSPORT.equals(nt.category)) continue;
                                CharSequence title = nt.extras.getCharSequence(Notification.EXTRA_TITLE);
                                CharSequence text = nt.extras.getCharSequence(Notification.EXTRA_TEXT);
                                if (title == null && text == null) continue;
                                JSONObject o = new JSONObject();
                                o.put("a", appLabel(sbn.getPackageName()));
                                o.put("t", title == null ? "" : title.toString());
                                o.put("x", text == null ? "" : text.toString());
                                n.put(o);
                            }
                        }
                    }
                    root.put("n", n);
                }
            } catch (Exception e) {
                try {
                    root.put("perm", false);
                } catch (Exception ignored) {
                }
            }
            return root.toString();
        }

        @JavascriptInterface
        public String getArt() {
            try {
                MediaController c = activeController();
                if (c == null) return "";
                MediaMetadata md = c.getMetadata();
                Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (b == null) return "";
                int side = Math.min(b.getWidth(), b.getHeight());
                Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
                Bitmap sc = Bitmap.createScaledBitmap(sq, 360, 360, true);
                return jpegUrl(sc, 85);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void mediaControl(String cmd) {
            try {
                MediaController c = activeController();
                if (c == null) return;
                MediaController.TransportControls t = c.getTransportControls();
                if ("next".equals(cmd)) {
                    t.skipToNext();
                } else if ("prev".equals(cmd)) {
                    t.skipToPrevious();
                } else {
                    PlaybackState st = c.getPlaybackState();
                    if (st != null && st.getState() == PlaybackState.STATE_PLAYING) t.pause();
                    else t.play();
                }
            } catch (Exception ignored) {
            }
        }

        private String photoPerm() {
            return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        @JavascriptInterface
        public boolean hasPhotoPerm() {
            return checkSelfPermission(photoPerm()) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void requestPhotoPerm() {
            runOnUiThread(new Runnable() {
                public void run() {
                    requestPermissions(new String[]{photoPerm()}, 11);
                }
            });
        }

        @JavascriptInterface
        public String getPhotos() {
            JSONArray arr = new JSONArray();
            try {
                Cursor cur = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                        null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
                if (cur != null) {
                    int k = 0;
                    while (cur.moveToNext() && k < 40) {
                        JSONObject o = new JSONObject();
                        o.put("id", cur.getLong(0));
                        o.put("n", cur.getString(1));
                        arr.put(o);
                        k++;
                    }
                    cur.close();
                }
            } catch (Exception ignored) {
            }
            return arr.toString();
        }

        private Bitmap decode(InputStream is0, Uri uri, int max) throws Exception {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream is = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(is, null, o);
            is.close();
            int ss = 1;
            while (o.outWidth / (ss * 2) >= max && o.outHeight / (ss * 2) >= max) ss *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = ss;
            is = getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(is, null, o2);
            is.close();
            if (b == null) return null;
            int deg = 0;
            try {
                is = getContentResolver().openInputStream(uri);
                ExifInterface ex = new ExifInterface(is);
                int or = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                is.close();
                if (or == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
                else if (or == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
                else if (or == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
            } catch (Exception ignored) {
            }
            if (deg != 0) {
                Matrix mt = new Matrix();
                mt.postRotate(deg);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), mt, true);
            }
            float sc = Math.min(1f, (float) max / Math.max(b.getWidth(), b.getHeight()));
            if (sc < 1f) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * sc), Math.round(b.getHeight() * sc), true);
            return b;
        }

        private String jpegUrl(Bitmap b, int q) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, q, out);
            return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        }

        private Uri photoUri(String id) {
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Long.parseLong(id));
        }

        @JavascriptInterface
        public String getThumb(String id) {
            try {
                Bitmap b = decode(null, photoUri(id), 320);
                return b == null ? "" : jpegUrl(b, 75);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String saveWallpaper(String id) {
            try {
                Bitmap b = decode(null, photoUri(id), 1920);
                if (b == null) return "";
                FileOutputStream fo = openFileOutput("wp.jpg", Context.MODE_PRIVATE);
                b.compress(Bitmap.CompressFormat.JPEG, 85, fo);
                fo.close();
                return jpegUrl(b, 85);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String getWallpaper() {
            try {
                InputStream is = new FileInputStream(getFileStreamPath("wp.jpg"));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
                is.close();
                return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }
    }
}
