package com.ownmotion.ai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;

/** CPU-rendered image animation encoded as MP4. This is not a generative ML model. */
public final class CpuVideoRenderer {
    private static final int WIDTH = 720, HEIGHT = 720, FPS = 15, BIT_RATE = 2_000_000;
    private CpuVideoRenderer() {}

    private static final class MuxState {
        int track = -1;
        boolean started = false;
    }

    public static void render(Bitmap source, String caption, File output, int seconds) throws Exception {
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        Surface surface = null;
        MuxState state = new MuxState();
        boolean completed = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = codec.createInputSurface();
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            codec.start();

            int frames = Math.max(30, Math.min(150, seconds * FPS));
            Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
            shade.setColor(0x99000000);
            Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            captionPaint.setColor(0xffffffff);
            captionPaint.setTextSize(27f);
            captionPaint.setShadowLayer(3f, 1f, 2f, 0xff000000);

            for (int frame = 0; frame < frames; frame++) {
                float p = frame / (float)(frames - 1);
                Canvas canvas = surface.lockCanvas(null);
                try {
                    canvas.drawColor(0xff080d18);
                    drawKenBurns(canvas, source, p, imagePaint);
                    if (caption != null && !caption.isEmpty()) {
                        canvas.drawRect(0, HEIGHT - 92, WIDTH, HEIGHT, shade);
                        String line = caption.length() > 76 ? caption.substring(0, 73) + "..." : caption;
                        canvas.drawText(line, 24, HEIGHT - 38, captionPaint);
                    }
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
                drain(codec, muxer, info, state, false);
                if ((frame % 4) == 0) Thread.sleep(5);
            }

            codec.signalEndOfInputStream();
            long deadline = System.currentTimeMillis() + 30000;
            while (!completed && System.currentTimeMillis() < deadline) {
                completed = drain(codec, muxer, info, state, true);
            }
            if (!completed) throw new Exception("ভিডিও encoder সময়মতো শেষ হয়নি");
            if (!state.started) throw new Exception("MP4 track তৈরি হয়নি");
        } catch (Exception e) {
            if (output.exists()) output.delete();
            throw e;
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                codec.release();
            }
            if (surface != null) surface.release();
            if (muxer != null) {
                try { if (state.started) muxer.stop(); } catch (Exception ignored) {}
                muxer.release();
            }
        }
    }

    /** Returns true after encoder EOS has been written. */
    private static boolean drain(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info,
                                 MuxState state, boolean waitForEos) {
        boolean eos = false;
        while (true) {
            int index = codec.dequeueOutputBuffer(info, waitForEos ? 10_000 : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) break;
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (state.started) throw new IllegalStateException("Encoder format changed twice");
                state.track = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                state.started = true;
                continue;
            }
            if (index >= 0) {
                ByteBuffer data = codec.getOutputBuffer(index);
                if (data != null && info.size > 0 && state.started &&
                        (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    data.position(info.offset);
                    data.limit(info.offset + info.size);
                    muxer.writeSampleData(state.track, data, info);
                }
                eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(index, false);
                if (eos) break;
            }
        }
        return eos;
    }

    private static void drawKenBurns(Canvas canvas, Bitmap bitmap, float p, Paint paint) {
        float bw = bitmap.getWidth(), bh = bitmap.getHeight();
        float scale = Math.max(WIDTH / bw, HEIGHT / bh);
        float baseW = bw * scale, baseH = bh * scale;
        float zoom = 1.0f + 0.10f * p;
        float drawW = baseW * zoom, drawH = baseH * zoom;
        float driftX = (baseW - WIDTH) * (0.5f - p * 0.18f);
        float driftY = (baseH - HEIGHT) * (0.5f + p * 0.12f);
        RectF dst = new RectF((WIDTH - drawW) / 2f - driftX, (HEIGHT - drawH) / 2f - driftY,
                (WIDTH + drawW) / 2f - driftX, (HEIGHT + drawH) / 2f - driftY);
        canvas.drawBitmap(bitmap, null, dst, paint);
    }
}
