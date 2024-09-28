package it.dhd.oxygencustomizer.xposed.hooks.systemui.aod;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.dhd.oxygencustomizer.utils.Constants.ACTION_WEATHER_INFLATED;
import static it.dhd.oxygencustomizer.utils.Constants.LockscreenWeather.LOCKSCREEN_WEATHER_SWITCH;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.SYSTEM_UI;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_BIG_ACTIVE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_BIG_INACTIVE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_CUSTOM_COLOR;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_DEVICE_WIDGET;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_ENABLED;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_EXTRAS;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_SCALE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_SMALL_ACTIVE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE;
import static it.dhd.oxygencustomizer.utils.Constants.Preferences.LockscreenWidgets.LOCKSCREEN_WIDGETS_SMALL_INACTIVE;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.xposed.XposedMods;
import it.dhd.oxygencustomizer.xposed.views.LockscreenWidgetsView;

public class AodWidgets extends XposedMods {

    private static final String listenPackage = SYSTEM_UI;
    private final String TAG = "AodWidgets-->";

    private ViewGroup mAodRootLayout = null;

    // Lockscreen Widgets
    private LinearLayout mWidgetsContainer = new LinearLayout(mContext);
    private boolean mWeatherEnabled = false;
    private boolean mWeatherInflated = false;
    private boolean mWidgetsEnabled = false;
    private boolean mDeviceWidgetEnabled = false;
    private boolean mDeviceCustomColor = false;
    private int mDeviceLinearColor = Color.WHITE;
    private int mDeviceCircularColor = Color.WHITE;
    private int mDeviceTextColor = Color.WHITE;
    private boolean mWidgetsCustomColor = false;
    private int mBigInactiveColor = Color.BLACK;
    private int mBigActiveColor = Color.WHITE;
    private int mSmallInactiveColor = Color.BLACK;
    private int mSmallActiveColor = Color.WHITE;
    private int mBigIconActiveColor = Color.WHITE;
    private int mBigIconInactiveColor = Color.BLACK;
    private int mSmallIconActiveColor = Color.WHITE;
    private int mSmallIconInactiveColor = Color.BLACK;
    private String mDeviceName = "";
    private String mMainWidgets;
    private String mExtraWidgets;
    private float mWidgetsScale = 1f;
    private Object mActivityStarter = null;

    private boolean mReceiverRegistered = false;

    public AodWidgets(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        // Widgets
        mWeatherEnabled = Xprefs.getBoolean(LOCKSCREEN_WEATHER_SWITCH, false);
        mWidgetsEnabled = Xprefs.getBoolean(LOCKSCREEN_WIDGETS_ENABLED, false);
        mDeviceWidgetEnabled = Xprefs.getBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET, false);
        mMainWidgets = Xprefs.getString(LOCKSCREEN_WIDGETS, "");
        mExtraWidgets = Xprefs.getString(LOCKSCREEN_WIDGETS_EXTRAS, "");
        mDeviceCustomColor = Xprefs.getBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH, false);
        mDeviceLinearColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR, Color.WHITE);
        mDeviceCircularColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR, Color.WHITE);
        mDeviceTextColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR, Color.WHITE);
        mDeviceName = Xprefs.getString(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE, "");
        mWidgetsCustomColor = Xprefs.getBoolean(LOCKSCREEN_WIDGETS_CUSTOM_COLOR, false);
        mBigInactiveColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_BIG_INACTIVE, Color.BLACK);
        mBigActiveColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_BIG_ACTIVE, Color.WHITE);
        mSmallInactiveColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_SMALL_INACTIVE, Color.BLACK);
        mSmallActiveColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_SMALL_ACTIVE, Color.WHITE);
        mBigIconActiveColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE, Color.BLACK);
        mBigIconInactiveColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE, Color.WHITE);
        mSmallIconActiveColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE, Color.BLACK);
        mSmallIconInactiveColor = Xprefs.getInt(LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE, Color.WHITE);
        mWidgetsScale = Xprefs.getSliderFloat(LOCKSCREEN_WIDGETS_SCALE, 1.0f);

        if (Key[0].equals(LOCKSCREEN_WIDGETS_ENABLED) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_DEVICE_WIDGET) ||
                Key[0].equals(LOCKSCREEN_WIDGETS) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_EXTRAS)) {
            updateLockscreenWidgets();
        }
        if (Key[0].equals(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE)) {
            updateLsDeviceWidget();
        }
        if (Key[0].equals(LOCKSCREEN_WIDGETS_CUSTOM_COLOR) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_BIG_ACTIVE) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_BIG_INACTIVE) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_SMALL_ACTIVE) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_SMALL_INACTIVE) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE) ||
                Key[0].equals(LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE)) {
            updateLockscreenWidgetsColors();
        }
        if (Key[0].equals(LOCKSCREEN_WIDGETS_SCALE)) {
            updateLockscreenWidgetsScale();
        }

    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            if (intent != null && intent.getAction() != null && intent.getAction().equals(ACTION_WEATHER_INFLATED)) {
                mWeatherInflated = true;
//                placeLockscreenWidgets();
            }
        }
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        // Receiver to handle weather inflated
        if (!mReceiverRegistered) {
            mReceiverRegistered = true;
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_WEATHER_INFLATED);
            mContext.registerReceiver(mReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        }

        mWidgetsContainer.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        mWidgetsContainer.setGravity(CENTER_HORIZONTAL);

        try {
            Class<?> KeyguardQuickAffordanceInteractor = findClass("com.android.systemui.keyguard.domain.interactor.KeyguardQuickAffordanceInteractor", lpparam.classLoader);
            hookAllConstructors(KeyguardQuickAffordanceInteractor, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mActivityStarter = getObjectField(param.thisObject, "activityStarter");
                    setActivityStarter();
                }
            });
        } catch (Throwable ignored) {
        }

        Object OplusBlackScreenGestureControllEx = null;
        try {
//            OplusBlackScreenGestureControllEx oplusBlackScreenGestureControll = ((OplusKeyguardDependencyEx) DependencyEx.get(OplusKeyguardDependencyEx.class)).getOplusBlackScreenGestureControll();
            Class<?> DependencyEx = findClass("com.android.systemui.DependencyEx", lpparam.classLoader);
            Class<?> OplusKeyguardDependencyEx = findClass("com.android.keyguard.OplusKeyguardDependencyEx", lpparam.classLoader);
            //OplusBlackScreenGestureControllEx = callStaticMethod(DependencyEx, "get", OplusKeyguardDependencyEx);

        } catch (Throwable t) {
            log(TAG + "OplusBlackScreenGestureControllEx not found");
        }

        try {
            Class<?> AodSceneGestureCallback = findClass("com.oplus.systemui.aod.scene.AodSceneViewHolder$AodSceneGestureCallback", lpparam.classLoader);
            hookAllConstructors(AodSceneGestureCallback, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[1] = mWidgetsContainer;
                }
            });
        } catch (Throwable t) {
            log(TAG + "OplusBlackScreenGestureControllEx not found");
        }

        try {
            Class<?> OplusBlackScreenGestureControllExImpl = findClass("com.oplus.systemui.keyguard.gesture.OplusBlackScreenGestureControllExImpl", lpparam.classLoader);
            hookAllMethods(OplusBlackScreenGestureControllExImpl, "onReceiveGesture", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    log(TAG + "onReceiveGesture: " + param.args[0]);
                }
            });
        } catch (Throwable t) {
            log(TAG + "OplusBlackScreenGestureControllEx not found");
        }

        if (Build.VERSION.SDK_INT == 33) {
            try {
                Class<?> KeyguardBottomAreaView = findClass("com.android.systemui.statusbar.phone.KeyguardBottomAreaView", lpparam.classLoader);
                hookAllMethods(KeyguardBottomAreaView, "onFinishInflate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        mActivityStarter = getObjectField(param.thisObject, "mActivityStarter");
                        setActivityStarter();
                    }
                });
            } catch (Throwable ignored) {
            }
        }

        Class<?> AodClockLayout;

        try {
            AodClockLayout = findClass("com.oplus.systemui.aod.aodclock.off.AodClockLayout", lpparam.classLoader);
        } catch (Throwable t) {
            AodClockLayout = findClass("com.oplusos.systemui.aod.aodclock.off.AodClockLayout", lpparam.classLoader); //OOS 13
        }


        hookAllMethods(AodClockLayout, "initForAodApk", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                //if (!mAodClockEnabled) return;
                FrameLayout mAodViewFromApk = (FrameLayout) getObjectField(param.thisObject, "mAodViewFromApk");
                for (int i = 0; i < mAodViewFromApk.getChildCount(); i++) {
                    if (mAodViewFromApk.getChildAt(i) instanceof ViewGroup v) {
                        mAodRootLayout = v;
                    }
                }
                placeLockscreenWidgets();
            }
        });

    }

    private void placeLockscreenWidgets() {
        if (!mWidgetsEnabled) return;
        try {
            LockscreenWidgetsView lsWidgets = LockscreenWidgetsView.getInstance(mContext, mActivityStarter);
            try {
                ((ViewGroup) lsWidgets.getParent()).removeView(lsWidgets);
            } catch (Throwable ignored) {
            }
            mAodRootLayout.addView(lsWidgets, mAodRootLayout.getChildCount());
            updateLockscreenWidgets();
            updateLsDeviceWidget();
            updateLockscreenWidgetsColors();
            updateLockscreenWidgetsScale();
        } catch (Throwable ignored) {
        }
    }

    private void updateLockscreenWidgets() {
        LockscreenWidgetsView lsWidgets = LockscreenWidgetsView.getInstance();
        if (lsWidgets == null) return;
        lsWidgets.setOptions(mWidgetsEnabled, mDeviceWidgetEnabled, mMainWidgets, mExtraWidgets);
    }

    private void updateLsDeviceWidget() {
        LockscreenWidgetsView lsWidgets = LockscreenWidgetsView.getInstance();
        if (lsWidgets == null) return;
        lsWidgets.setDeviceWidgetOptions(mDeviceCustomColor, mDeviceLinearColor, mDeviceCircularColor, mDeviceTextColor, mDeviceName);
    }

    private void updateLockscreenWidgetsColors() {
        LockscreenWidgetsView lsWidgets = LockscreenWidgetsView.getInstance();
        if (lsWidgets == null) return;
        lsWidgets.setCustomColors(
                mWidgetsCustomColor,
                mBigInactiveColor, mBigActiveColor,
                mSmallInactiveColor, mSmallActiveColor,
                mBigIconInactiveColor, mBigIconActiveColor,
                mSmallIconInactiveColor, mSmallIconActiveColor);
    }

    private void updateLockscreenWidgetsScale() {
        LockscreenWidgetsView lsWidgets = LockscreenWidgetsView.getInstance();
        if (lsWidgets == null) return;
        lsWidgets.setScale(mWidgetsScale);
    }

    private void setActivityStarter() {
        LockscreenWidgetsView lsWidgets = LockscreenWidgetsView.getInstance();
        if (lsWidgets == null) return;
        lsWidgets.setActivityStarter(mActivityStarter);
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}