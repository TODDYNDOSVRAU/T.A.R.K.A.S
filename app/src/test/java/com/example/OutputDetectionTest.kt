package com.example

import android.media.AudioDeviceInfo
import com.example.data.audio.OutputDetection
import com.example.data.audio.OutputDevice
import com.example.data.audio.OutputKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** E10: pins how an Android device becomes the output the app knows, as it was inside the manager. */
class OutputDetectionTest {
  private val speaker = OutputDevice.speaker("Alto-falante")
  private fun out(type: Int, product: String?, address: String?) =
    OutputDetection.outputFor(type, product, address, speaker) { "Rota" }

  @Test fun theKindFollowsTheAndroidType() {
    assertEquals(OutputKind.WIRED, OutputDetection.kindOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
    assertEquals(OutputKind.WIRED, OutputDetection.kindOf(AudioDeviceInfo.TYPE_WIRED_HEADSET))
    assertEquals(OutputKind.BLUETOOTH, OutputDetection.kindOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
    assertEquals(OutputKind.USB, OutputDetection.kindOf(AudioDeviceInfo.TYPE_USB_HEADSET))
    assertEquals(OutputKind.USB, OutputDetection.kindOf(AudioDeviceInfo.TYPE_USB_DEVICE))
    assertEquals(OutputKind.SPEAKER, OutputDetection.kindOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    assertEquals(OutputKind.SPEAKER, OutputDetection.kindOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
    assertEquals(OutputKind.OTHER, OutputDetection.kindOf(AudioDeviceInfo.TYPE_HDMI))
  }

  @Test fun builtInOutputsAreTheLoudspeakerWhateverTheyCallThemselves() {
    assertEquals(speaker, out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "sdk_gphone64", "0"))
    assertEquals(speaker, out(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "sdk_gphone64", null))
  }

  @Test fun aWiredFoneHasNoNameOfItsOwnAndIsOneOutput() {
    val o = out(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Pixel 9", "")
    assertEquals(OutputDevice("wired", OutputKind.WIRED, "Rota"), o)
  }

  @Test fun aBluetoothFoneIsToldApartByItsAddress() {
    val o = out(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "WH-1000XM5", "AA:BB")
    assertEquals(OutputDevice("bt:AA:BB", OutputKind.BLUETOOTH, "WH-1000XM5"), o)
  }

  @Test fun withoutAnAddressTheNameTellsThemApart() {
    val o = out(AudioDeviceInfo.TYPE_USB_HEADSET, "My DAC", null)
    assertEquals(OutputDevice("usb:my dac", OutputKind.USB, "My DAC"), o)
  }

  @Test fun aBlankNameFallsBackToTheRouteName() {
    val o = out(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "  ", "AA:BB")
    assertEquals("Rota", o.name)
    assertEquals("bt:AA:BB", o.key)
  }
}
