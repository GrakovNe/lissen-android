package org.grakovne.lissen.playback.service

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor

/**
 * Extractors for the playback pipeline.
 *
 * Artwork embedded into MP4/M4B files is never shown by Lissen: covers always come from the server
 * through `ExternalCoverProvider`, and the session only ever sees that cover URI. Left enabled, media3
 * still keeps a copy of the embedded picture in every chapter's track format and one more in the
 * player's internal media metadata. A multi-megabyte cover then pushes the heap over the limit on
 * every chapter switch and playback stops with an OutOfMemoryError (#534), so MP4 artwork parsing
 * is disabled.
 */
@UnstableApi
fun playbackExtractorsFactory(): ExtractorsFactory =
  DefaultExtractorsFactory().setMp4ExtractorFlags(Mp4Extractor.FLAG_DISABLE_ARTWORK_METADATA)
