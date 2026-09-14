package cn.pickup.launcher;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

final class DeepLinkLauncher {
    private static final String TAG = "PickupDeepLink";

    /**
     * Deep links tried against the Douyin Mall app only. The mall app shares the
     * snssdk1128:// scheme family with the main Douyin app; scoping the intent to
     * the mall package keeps the main app from hijacking the route.
     */
    private static final String[] DOUYIN_MALL_URIS = {
            "snssdk1128://profile",
            "snssdk1128://mall"
    };

    private DeepLinkLauncher() {
    }

    static void open(Context context, Destination destination) {
        // The Douyin Mall app (com.ss.android.ugc.livelite) does not register the
        // bare snssdk1128:// scheme, so the main Douyin app would always win the
        // deep-link race and open the feed instead. Try mall-only deep links
        // (profile opens the account page that contains the order entry), then
        // launch the mall app directly.
        if (destination == Destination.DOUYIN_PENDING
                && isPackageInstalled(context, "com.ss.android.ugc.livelite")) {
            for (String mallUri : DOUYIN_MALL_URIS) {
                if (tryOpenScopedToPackage(context, mallUri, "com.ss.android.ugc.livelite")) {
                    Toast.makeText(context, "已打开抖音商城「我的」，点「我的订单」查看待收货", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            if (tryOpenInstalledApp(context, destination)) {
                Toast.makeText(context, "已打开抖音商城，点「我的订单」查看待收货", Toast.LENGTH_LONG).show();
                return;
            }
        }

        for (String appUri : destination.appUris) {
            if (tryOpen(context, appUri, destination)) {
                return;
            }
        }

        if (destination.openAppWhenDeepLinkUnavailable) {
            if (tryOpenInstalledApp(context, destination)) {
                String hint;
                if (destination == Destination.DOUYIN_PENDING) {
                    hint = "已打开抖音商城，点「我的订单」查看待收货";
                } else if (destination == Destination.KUAISHOU_PENDING) {
                    hint = "已打开快手订单页，点「待收货」查看";
                } else {
                    hint = destination.title + "未找到直达页面，已打开官方 App，请在 App 内进入对应入口";
                }
                Toast.makeText(context, hint, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(
                        context,
                        "未安装" + destination.title + "，请先安装对应 App 后重试",
                        Toast.LENGTH_LONG
                ).show();
            }
            return;
        }

        // Some versions of the official apps can render their own HTTPS pages.
        // Try that route before handing the URL to the user's browser.
        if (tryOpen(context, destination.webUri, destination)) {
            return;
        }

        if (!tryOpen(context, destination.webUri, null)) {
            Toast.makeText(context, "暂时无法打开" + destination.title, Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean tryOpenScopedToPackage(Context context, String uri, String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return resolveAndStart(context, intent, uri, packageName);
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private static boolean tryOpenInstalledApp(Context context, Destination destination) {
        for (String packageName : destination.candidatePackages()) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent == null) {
                Log.i(TAG, "No launch activity found for package " + packageName);
                continue;
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (!(context instanceof android.app.Activity)) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            try {
                context.startActivity(launchIntent);
                Log.i(TAG, "Opened installed app " + packageName);
                return true;
            } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException exception) {
                Log.w(TAG, "Could not open installed app " + packageName, exception);
            }
        }
        return false;
    }

    private static boolean tryOpen(Context context, String uri, Destination destination) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        // Try each candidate package in order (primary, mall/lite variants).
        // This lets e.g. Douyin Mall be preferred when it is installed.
        for (String packageName : destination.candidatePackages()) {
            Intent scoped = new Intent(intent);
            scoped.setPackage(packageName);
            if (resolveAndStart(context, scoped, uri, packageName)) {
                return true;
            }
        }

        // Last chance before the web fallback: let the system pick any app
        // that registered the scheme. This covers packages we did not list.
        return resolveAndStart(context, intent, uri, null);
    }

    private static boolean resolveAndStart(Context context, Intent intent, String uri, String label) {
        try {
            ComponentName resolved = intent.resolveActivity(context.getPackageManager());
            if (resolved == null) {
                Log.i(TAG, "No activity resolved for " + uri + " with " + label);
                return false;
            }
            context.startActivity(intent);
            Log.i(TAG, "Opened " + uri + " with " + resolved.flattenToShortString());
            return true;
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Could not open " + uri + " with " + label, exception);
            return false;
        }
    }
}
