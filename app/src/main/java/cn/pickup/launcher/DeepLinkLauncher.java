package cn.pickup.launcher;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

final class DeepLinkLauncher {
    private static final String TAG = "PickupDeepLink";

    private DeepLinkLauncher() {
    }

    static void open(Context context, Destination destination) {
        for (String appUri : destination.appUris) {
            if (tryOpen(context, appUri, destination.packageName)) {
                return;
            }
        }

        if (destination.openAppWhenDeepLinkUnavailable
                && tryOpenInstalledApp(context, destination)) {
            Toast.makeText(
                    context,
                    destination.title + "未找到直达页面，已打开官方 App，请在 App 内进入对应入口",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        // Some versions of the official apps can render their own HTTPS pages.
        // Try that route before handing the URL to the user's browser.
        if (tryOpen(context, destination.webUri, destination.packageName)) {
            return;
        }

        if (!tryOpen(context, destination.webUri, null)) {
            Toast.makeText(context, "暂时无法打开" + destination.title, Toast.LENGTH_SHORT).show();
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

    private static boolean tryOpen(Context context, String uri, String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        if (packageName != null) {
            Intent scoped = new Intent(intent);
            scoped.setPackage(packageName);
            if (resolveAndStart(context, scoped, uri, packageName)) {
                return true;
            }
        }

        // Last chance before the web fallback: let the system pick any app
        // that registered the scheme. This covers lite/variant packages
        // whose ids differ from the primary one (e.g. Kuaishou Nebula).
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
