/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.widget;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class to working with {@link AppWidgetManager}
 */
public class WidgetManagerHelper {

    final AppWidgetManager mAppWidgetManager;
    final Context mContext;

    public WidgetManagerHelper(Context context) {
        mContext = context;
        mAppWidgetManager = AppWidgetManager.getInstance(context);
    }

    /**
     * @see AppWidgetManager#getAppWidgetInfo(int)
     */
    public LauncherAppWidgetProviderInfo getLauncherAppWidgetInfo(int appWidgetId) {
        if (appWidgetId <= LauncherAppWidgetInfo.CUSTOM_WIDGET_ID) {
            return CustomWidgetManager.INSTANCE.get(mContext).getWidgetProvider(appWidgetId);
        }
        AppWidgetProviderInfo info = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        return info == null ? null : LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, info);
    }

    /**
     * @see AppWidgetManager#getInstalledProvidersForPackage(String, UserHandle)
     */
    @TargetApi(Build.VERSION_CODES.O)
    public List<AppWidgetProviderInfo> getAllProviders(@Nullable PackageUserKey packageUser) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return Collections.emptyList();
        }

        if (packageUser == null) {
            return allWidgetsSteam(mContext).collect(Collectors.toList());
        }

        if (Utilities.ATLEAST_OREO) {
            return mAppWidgetManager.getInstalledProvidersForPackage(
                    packageUser.mPackageName, packageUser.mUser);
        }

        String pkg = packageUser.mPackageName;
        return Stream.concat(
                // Only get providers for the given package/user.
                mAppWidgetManager.getInstalledProvidersForProfile(packageUser.mUser)
                        .stream()
                        .filter(w -> w.provider.equals(pkg)),
                Process.myUserHandle().equals(packageUser.mUser)
                        && mContext.getPackageName().equals(pkg)
                        ? CustomWidgetManager.INSTANCE.get(mContext).stream()
                        : Stream.empty())
                .collect(Collectors.toList());
    }

    /**
     * @see AppWidgetManager#bindAppWidgetIdIfAllowed(int, UserHandle, ComponentName, Bundle)
     */
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, AppWidgetProviderInfo info,
            Bundle options) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return false;
        }
        if (appWidgetId <= LauncherAppWidgetInfo.CUSTOM_WIDGET_ID) {
            return true;
        }
        return mAppWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId, info.getProfile(), info.provider, options);
    }

    public LauncherAppWidgetProviderInfo findProvider(ComponentName provider, UserHandle user) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return null;
        }
        for (AppWidgetProviderInfo info :
                getAllProviders(new PackageUserKey(provider.getPackageName(), user))) {
            if (info.provider.equals(provider)) {
                return LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, info);
            }
        }
        return null;
    }

    public static Map<ComponentKey, AppWidgetProviderInfo> getAllProvidersMap(Context context) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return Collections.emptyMap();
        }
        return allWidgetsSteam(context).collect(
                        Collectors.toMap(info -> new ComponentKey(info.provider, info.getProfile()),
                        Function.identity()));
    }

    private static Stream<AppWidgetProviderInfo> allWidgetsSteam(Context context) {
        AppWidgetManager awm = context.getSystemService(AppWidgetManager.class);
        return Stream.concat(
                UserCache.INSTANCE.get(context)
                        .getUserProfiles()
                        .stream()
                        .flatMap(u -> awm.getInstalledProvidersForProfile(u).stream()),
                CustomWidgetManager.INSTANCE.get(context).stream());
    }
}
