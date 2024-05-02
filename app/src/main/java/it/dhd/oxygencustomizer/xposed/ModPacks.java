package it.dhd.oxygencustomizer.xposed;

import java.util.ArrayList;

import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.hooks.HookTester;
import it.dhd.oxygencustomizer.xposed.hooks.framework.Buttons;
import it.dhd.oxygencustomizer.xposed.hooks.framework.PhoneWindowManager;
import it.dhd.oxygencustomizer.xposed.hooks.launcher.Launcher;
import it.dhd.oxygencustomizer.xposed.hooks.screenshot.ScreenshotSecureFlag;
import it.dhd.oxygencustomizer.xposed.hooks.settings.CustomShortcut;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.AdaptivePlayback;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.AudioDataProvider;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.BatteryDataProvider;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.FeatureEnabler;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.MiscMods;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.OpUtils;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.PulseViewHook;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.SettingsLibUtilsProvider;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.VolumePanel;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.lockscreen.Lockscreen;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.lockscreen.LockscreenClock;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.navbar.GestureNavbarManager;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.BatteryBar;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.BatteryStyleManager;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.HeaderClock;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.HeaderImage;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.QSTiles;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.QsTileCustomization;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.StatusbarClock;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.StatusbarIcons;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.StatusbarMods;
import it.dhd.oxygencustomizer.xposed.hooks.systemui.statusbar.StatusbarNotification;

public class ModPacks {

    public static ArrayList<Class<? extends XposedMods>> getMods(String packageName) {
        ArrayList<Class<? extends XposedMods>> modPacks = new ArrayList<>();

        //Should be loaded before others
        modPacks.add(HookTester.class);
        modPacks.add(SettingsLibUtilsProvider.class);

        switch (packageName) {
            case Constants.Packages.FRAMEWORK -> {
                modPacks.add(PhoneWindowManager.class);
                modPacks.add(Buttons.class);
            }
            case Constants.Packages.SYSTEM_UI -> {
                if (!XPLauncher.isChildProcess) {
                    // Battery Data Provider
                    modPacks.add(BatteryDataProvider.class);

                    // System Classes We need
                    modPacks.add(OpUtils.class);

                    // Oplus Feature Enabler
                    modPacks.add(FeatureEnabler.class);

                    // AOD
                    //modPacks.add(AOD.class);

                    // Statusbar
                    modPacks.add(StatusbarMods.class);
                    modPacks.add(HeaderClock.class);
                    modPacks.add(StatusbarNotification.class);
                    modPacks.add(StatusbarClock.class);
                    modPacks.add(StatusbarIcons.class);
                    modPacks.add(BatteryStyleManager.class);
                    // QS
                    modPacks.add(QSTiles.class);
                    modPacks.add(QsTileCustomization.class);
                    modPacks.add(HeaderImage.class);
                    // Pulse View
                    modPacks.add(AudioDataProvider.class);
                    modPacks.add(PulseViewHook.class);

                    // Lockscreen
                    modPacks.add(Lockscreen.class);
                    modPacks.add(LockscreenClock.class);

                    // Volume Panel
                    modPacks.add(VolumePanel.class);

                    modPacks.add(GestureNavbarManager.class);
                    modPacks.add(BatteryBar.class);
                    modPacks.add(AdaptivePlayback.class);

                    modPacks.add(MiscMods.class);
                }
            }
            case Constants.Packages.SETTINGS -> modPacks.add(CustomShortcut.class);
            case Constants.Packages.LAUNCHER -> modPacks.add(Launcher.class);
            case Constants.Packages.SCREENSHOT -> modPacks.add(ScreenshotSecureFlag.class);
        }

        return modPacks;
    }
}
