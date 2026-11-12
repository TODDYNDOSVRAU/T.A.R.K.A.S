package com.example.data.audio.dsp

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * The sink's processing chain with only our processors. The default chain appends Sonic (speed) and
 * silence skipping after them, and both reject float PCM ("Unhandled input format", track init fails),
 * so nothing would play. The app has no speed or skip-silence control, so they are left out: speed stays
 * 1x and silence is not skipped.
 */
@OptIn(UnstableApi::class)
class OwnAudioProcessorChain(private val processors: Array<AudioProcessor>) : DefaultAudioSink.AudioProcessorChain {
  override fun getAudioProcessors(): Array<AudioProcessor> = processors
  override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters = PlaybackParameters.DEFAULT
  override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false
  override fun getMediaDuration(playoutDuration: Long): Long = playoutDuration
  override fun getSkippedOutputFrameCount(): Long = 0L
}
