package com.ownmotion.ai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * First-stage CPU image-to-video renderer.
 * Draws frames on the encoder input Surface; no ML model or GPU inference is used.
 */
public final class CpuVideoRenderer {
    private static final int WIDTH = 720;
    private static final int HEIGHT = 720;
    private static final int FPS = 15;
    private static final int BIT_RATE = 2_000_000;

    private CpuVideoRenderer() {}

    public static void render(Bitmap source, String caption, File output, int seconds) throws Exception {
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        Surface inputSurface = null;
        boolean muxerStarted = false;
        int track = -1;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = codec.createInputSurface();
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            codec.start();

            int frames = Math.max(30, Math.min(150, seconds * FPS));
            Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
            shade.setColor(0x99000000);
            Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            captionPaint.setColor(0xffffffff);
            captionPaint.setTextSize(28f);
            captionPaint.setShadowLayer(3f, 1f, 2f, 0xff000000);

            for (int frame = 0; frame < frames; frame++) {
                float progress = frame / (float)(frames - 1);
                Canvas canvas = inputSurface.lockCanvas(null);
                try {
                    canvas.drawColor(0xff080d18);
                    drawKenBurns(canvas, source, progress, imagePaint);
                    if (caption != null && !caption.isEmpty()) {
                        canvas.drawRect(0, HEIGHT - 92, WIDTH, HEIGHT, shade);
                        String line = caption.length() > 80 ? caption.substring(0, 77) + "..." : caption;
                        canvas.drawText(line, 28, HEIGHT - 38, captionPaint);
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas);
                }
                drain(codec, muxer, info, false, new TrackState());
                // Yield to avoid monopolising the phone's CPU.
                if ((frame % 5) == 0) Thread.sleep(8);
            }

            codec.signalEndOfInputStream();
            TrackState state = new TrackState();
            boolean done = false;
            long deadline = System.currentTimeMillis() + 15000;
            while (!done && System.currentTimeMillis() < deadline) {
                int index = codec.dequeueOutputBuffer(info, 10_000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw new IllegalStateException("Encoder format changed twice");
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                } else if (index >= 0) {
                    ByteBuffer data = codec.getOutputBuffer(index);
                    if (data != null && info.size > 0 && muxerStarted && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        data.position(info.offset); data.limit(info.offset + info.size);
                        muxer.writeSampleData(track, data, info);
                    }
                    done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(index, false);
                }
            }
            if (!done) throw new Exception("ভিডিও encoder সময়মতো শেষ হয়নি");
            if (!muxerStarted) throw new Exception("MP4 encoder track তৈরি হয়নি");
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                codec.release();
            }
            if (inputSurface != null) inputSurface.release();
            if (muxer != null) {
                try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) {}
                muxer.release();
            }
        }
    }

    private static final class TrackState {}

    private static void drain(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info,
                              boolean end, TrackState ignored) {
        // The muxer track is registered on INFO_OUTPUT_FORMAT_CHANGED. This helper
        // intentionally drains only before EOS; output is handled in drainAll below.
        int index;
        while ((index = codec.dequeueOutputBuffer(info, 0)) >= 0) {
            codec.releaseOutputBuffer(index, false);
        }
    }

    private static void drawKenBurns(Canvas canvas, Bitmap bitmap, float p, Paint paint) {
        float bw = bitmap.getWidth(), bh = bitmap.getHeight();
        float scale = Math.max(WIDTH / bw, HEIGHT / bh);
        float baseW = bw * scale, baseH = bh * scale;
        float zoom = 1.0f + 0.10f * p;
        float drawW = baseW * zoom, drawH = baseH * zoom;
        float driftX = (baseW - WIDTH) * (0.5f - p * 0.18f);
        float driftY = (baseH - HEIGHT) * (0.5f + p * 0.12f);
        RectF dst = new RectF((WIDTH - drawW) / 2f - driftX,
                (HEIGHT - drawH) / 2f - driftY,
                (WIDTH + drawW) / 2f - driftX,
                (HEIGHT + drawH) / 2f - driftY);
        canvas.drawBitmap(bitmap, null, dst, paint);
    }
}
