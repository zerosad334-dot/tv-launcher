package com.zerosad.tvlauncher;

import android.app.Activity;
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
    }
                          }
