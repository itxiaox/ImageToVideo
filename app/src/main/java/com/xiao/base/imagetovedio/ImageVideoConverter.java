package com.xiao.base.imagetovedio;

/**
 * Created by xiao on 2018/1/4.
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

public class ImageVideoConverter {
    private static final String TAG = "ImageVideoConverter";
    private static final String OUTPUT_MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int TIMEOUT_USEC = 10000;
    private static final int frameRate = 30;
    private static final int bitrate = 700000;
    private static final int keyFrameInternal = 1;

    public static void convertImageToVideo(String filePath,
                                           float duration,
                                           int width,
                                           int height,
                                           final String videoFilePath ) {

        int nbFrames = (int)(duration * (float)frameRate) + 1;

        MediaMuxer muxer = null;
        MediaCodec codec = null;
        boolean muxerStarted = false;
        boolean codecStarted = false;
        int videoTrackIndex = -1;

        try {
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(OUTPUT_MIME, width, height);

            muxer = new MediaMuxer(videoFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            codec = MediaCodec.createEncoderByType(OUTPUT_MIME);

            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameInternal);

            try {
                codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            }
            catch(Exception ce) {
                Log.e(TAG, ce.getMessage());
            }

            Surface surface = codec.createInputSurface();
            codec.start();
            codecStarted = true;

            ByteBuffer[] outputBuffers = codec.getOutputBuffers();
            int outputBufferIndex;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean outputDone = false;
            int nbEncoded = 0;

            Bitmap frame = getBitmapFromImage(filePath, width, height);
            Canvas canvas = surface.lockCanvas(new Rect(0,0, width, height));

            canvas.drawBitmap(frame, 0, 0, new Paint());
            surface.unlockCanvasAndPost(canvas);

            while (!outputDone) {
                canvas = surface.lockCanvas(new Rect(0,0, 0, 0));
                surface.unlockCanvasAndPost(canvas);

                outputBufferIndex = codec.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    Log.d(TAG, "no output from encoder available");
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    outputBuffers = codec.getOutputBuffers();
                    Log.d(TAG, "encoder output buffers changed");
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if(muxerStarted)
                        throwException("format changed twice");
                    MediaFormat newFormat = codec.getOutputFormat();
                    videoTrackIndex = muxer.addTrack(newFormat);
                    muxer.start();
                    muxerStarted = true;
                } else if (outputBufferIndex < 0) {
                    throwException("unexpected result from encoder.dequeueOutputBuffer: " + outputBufferIndex);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = outputBuffers[outputBufferIndex];
                    if (encodedData == null) {
                        throwException("encoderOutputBuffer " + outputBufferIndex + " was null");
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        info.size = 0;
                    }

                    if (info.size != 0) {
                        if (!muxerStarted)
                            throwException("muxer hasn't started");

                        info.presentationTimeUs = computePresentationTime(nbEncoded, frameRate);

                        if(videoTrackIndex == -1)
                            throwException("video track not set yet");

                        muxer.writeSampleData(videoTrackIndex, encodedData, info);

                        nbEncoded++;

                        if(nbEncoded == nbFrames)
                            outputDone = true;
                    }

                    // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                    codec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }

            if (codec != null) {
                if (codecStarted) {codec.stop();}
                codec.release();
            }
            if (muxer != null) {
                if (muxerStarted) {muxer.stop();}
                muxer.release();
            }

        } catch (Exception e) {
            Log.e(TAG, "Encoding exception: " + e.toString());
        }
    }

    public static void convertImageToVideo(Bitmap frame,
                                           float duration,
                                           int width,
                                           int height,
                                           final String videoFilePath ) {

        int nbFrames = (int)(duration * (float)frameRate) + 1;

        MediaMuxer muxer = null;
        MediaCodec codec = null;
        boolean muxerStarted = false;
        boolean codecStarted = false;
        int videoTrackIndex = -1;

        try {
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(OUTPUT_MIME, width, height);

            muxer = new MediaMuxer(videoFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            codec = MediaCodec.createEncoderByType(OUTPUT_MIME);

            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameInternal);

            try {
                codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            }
            catch(Exception ce) {
                Log.e(TAG, ce.getMessage());
            }

            Surface surface = codec.createInputSurface();
            codec.start();
            codecStarted = true;

            ByteBuffer[] outputBuffers = codec.getOutputBuffers();
            int outputBufferIndex;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean outputDone = false;
            int nbEncoded = 0;

            Canvas canvas = surface.lockCanvas(new Rect(0,0, width, height));

            canvas.drawBitmap(frame, 0, 0, new Paint());
            surface.unlockCanvasAndPost(canvas);

            while (!outputDone) {

                canvas = surface.lockCanvas(new Rect(0,0, 0, 0));
                surface.unlockCanvasAndPost(canvas);

                outputBufferIndex = codec.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    Log.d(TAG, "no output from encoder available");
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    outputBuffers = codec.getOutputBuffers();
                    Log.d(TAG, "encoder output buffers changed");
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if(muxerStarted)
                        throwException("format changed twice");
                    MediaFormat newFormat = codec.getOutputFormat();
                    videoTrackIndex = muxer.addTrack(newFormat);
                    muxer.start();
                    muxerStarted = true;
                } else if (outputBufferIndex < 0) {
                    throwException("unexpected result from encoder.dequeueOutputBuffer: " + outputBufferIndex);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = outputBuffers[outputBufferIndex];
                    if (encodedData == null) {
                        throwException("encoderOutputBuffer " + outputBufferIndex + " was null");
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out and fed to the muxer when we got
                        // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                        Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        info.size = 0;
                    }

                    if (info.size != 0) {
                        if (!muxerStarted)
                            throwException("muxer hasn't started");

                        info.presentationTimeUs = computePresentationTime(nbEncoded, frameRate);

                        if(videoTrackIndex == -1)
                            throwException("video track not set yet");

                        muxer.writeSampleData(videoTrackIndex, encodedData, info);

                        nbEncoded++;
                        Log.d(TAG, "nbEncoded="+nbEncoded);
                        Log.d(TAG, "nbFrames="+nbFrames);
                        if(nbEncoded == nbFrames)
                            outputDone = true;
                    }

                    // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                    codec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }

            if (codec != null) {
                if (codecStarted) {codec.stop();}
                codec.release();
            }
            if (muxer != null) {
                if (muxerStarted) {muxer.stop();}
                muxer.release();
            }

        } catch (Exception e) {
            Log.e(TAG, "Encoding exception: " + e.toString());
        }
    }

    private static long computePresentationTime(int frameIndex, int frameRate) {
        return frameIndex * 1000000 / frameRate;
    }

    private static Bitmap getBitmapFromImage(String inputFilePath, int width, int height) {
        Bitmap bitmap = decodeSampledBitmapFromFile(inputFilePath, width, height);

        if(bitmap.getWidth() != width || bitmap.getHeight() != height) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, false);
            bitmap.recycle();
            return scaled;
        }
        else {
            return bitmap;
        }
    }

    private static Bitmap decodeSampledBitmapFromFile(String filePath,
                                                      int reqWidth,
                                                      int reqHeight) {

        final BitmapFactory.Options options = new BitmapFactory.Options();
        // Calculate inSampleSize
        // First decode with inJustDecodeBounds=true to check dimensions
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;
        return BitmapFactory.decodeFile(filePath, options);
    }

    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private static void throwException(String exp) {
        throw new RuntimeException(exp);
    }
}