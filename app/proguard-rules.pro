# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK proguard defaults.

# Keep Shizuku UserService entry point
-keep class net.harveywilliams.bluetoothbouncer.shizuku.BluetoothBouncerUserService { *; }

# Keep AIDL generated classes
-keep class net.harveywilliams.bluetoothbouncer.IBluetoothBouncerUserService { *; }
-keep class net.harveywilliams.bluetoothbouncer.IBluetoothBouncerUserService$* { *; }

# Keep Room entities
-keep class net.harveywilliams.bluetoothbouncer.data.** { *; }
