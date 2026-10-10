# Customization screen: its themes keep the launcher icon color (helpers/KeepIconColor.kt)
-keepclassmembers class org.fossify.commons.activities.CustomizationActivity {
    private java.util.LinkedHashMap predefinedThemes;
    private int curAppIconColor;
}

# EventBus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
