package com.example.data.audio

import android.media.AudioDeviceInfo
import com.example.data.model.AudioCapabilities

/**
 * Which output the music is on, decided from what Android reports about a device. Pure (the device
 * facts come in as values), so the rules are checked without a device.
 */
object OutputDetection {

  fun kindOf(type: Int): OutputKind = when (type) {
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputKind.WIRED
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputKind.BLUETOOTH
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> OutputKind.USB
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputKind.SPEAKER
    else -> OutputKind.OTHER
  }

  /**
   * @param speaker the loudspeaker as the engine names it (from resources)
   * @param routeName the neutral name of this kind of route, used when the device has no name of its own
   * @return the loudspeaker for built-in outputs; otherwise the output with its key and name. The jack
   *   reports the phone's model as its name, so a wired fone has none and is just its route name.
   */
  fun outputFor(type: Int, productName: String?, address: String?, speaker: OutputDevice, routeName: () -> String): OutputDevice {
    val kind = kindOf(type)
    if (kind == OutputKind.SPEAKER) return speaker
    val product = if (kind == OutputKind.WIRED) null else productName?.ifBlank { null }
    return OutputDevice(OutputDevice.keyFor(kind, address, product), kind, product ?: routeName())
  }

  /** The route type the engineering details show for an Android device type. */
  fun routeTypeOf(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioCapabilities.ROUTE_WIRED
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioCapabilities.ROUTE_BLUETOOTH
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> AudioCapabilities.ROUTE_USB
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioCapabilities.ROUTE_BUILTIN_SPEAKER
    else -> AudioCapabilities.ROUTE_OTHER
  }

  /**
   * The name shown for the route. Built-in outputs report the phone's model code as their product name
   * (e.g. "sdk_gphone64_x86_64"), which means nothing to a listener; only external devices get their own.
   */
  fun routeNameOf(type: Int, productName: String?, routeName: () -> String): String {
    val builtIn = type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    return if (builtIn) routeName() else productName?.ifBlank { null } ?: routeName()
  }
}
