package com.otaliastudios.cameraview.picture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.engine.Camera1Engine;
import com.otaliastudios.cameraview.engine.offset.Reference;
import com.otaliastudios.cameraview.engine.orchestrator.CameraState;
import com.otaliastudios.cameraview.internal.ExifHelper;
import com.otaliastudios.cameraview.size.Size;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A {@link PictureResult} that uses standard APIs.
 */
public class Full1PictureRecorder extends FullPictureRecorder {

    private final Camera mCamera;
    private final Camera1Engine mEngine;

    public Full1PictureRecorder(@NonNull PictureResult.Stub stub,
                                @NonNull Camera1Engine engine,
                                @NonNull Camera camera) {
        super(stub, engine);
        mEngine = engine;
        mCamera = camera;

        // We set the rotation to the camera parameters, but we don't know if the result will be
        // already rotated with 0 exif, or original with non zero exif. we will have to read EXIF.
        Camera.Parameters params = mCamera.getParameters();
        params.setRotation(mResult.rotation);
        mCamera.setParameters(params);
    }

    @Override
    public void take() {
        LOG.i("take() called.");
        // Stopping the preview callback is important on older APIs / emulators,
        // or takePicture can hang and leave the camera in a bad state.
        mCamera.setPreviewCallbackWithBuffer(null);
        mEngine.getFrameManager().release();
        try {
            mCamera.takePicture(
                    () -> {
                        LOG.i("take(): got onShutter callback.");
                        dispatchOnShutter(true);
                    },
                    null,
                    null,
                    (data, camera) -> {
                        LOG.i("take(): got picture callback.");
                        int exifRotation;
                        try {
                            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(data));
                            int exifOrientation = exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL);
                            exifRotation = ExifHelper.getOrientation(exifOrientation);
                        } catch (IOException e) {
                            exifRotation = 0;
                        }
                        byte[] rotatedData = getMirrorData(data, mResult.facing, exifRotation);
                        mResult.data = rotatedData;
                        mResult.rotation = exifRotation;
                        LOG.i("take(): starting preview again. ", Thread.currentThread());

                        // It's possible that by the time this callback is invoked, we're not previewing
                        // anymore, so check before restarting preview.
                        if (mEngine.getState().isAtLeast(CameraState.PREVIEW)) {
                            camera.setPreviewCallbackWithBuffer(mEngine);
                            Size previewStreamSize = mEngine.getPreviewStreamSize(Reference.SENSOR);
                            if (previewStreamSize == null) {
                                throw new IllegalStateException("Preview stream size " +
                                        "should never be null here.");
                            }
                            // Need to re-setup the frame manager, otherwise no frames are processed
                            // after takePicture() is called
                            mEngine.getFrameManager().setUp(
                                    mEngine.getFrameProcessingFormat(),
                                    previewStreamSize,
                                    mEngine.getAngles()
                            );
                            camera.startPreview();
                        }
                        dispatchResult();
                    }
            );
            LOG.i("take() returned.");
        } catch (Exception e) {
            mError = e;
            dispatchResult();
        }
    }

    private byte[] getMirrorData(byte[] data, Facing facing, int exifRotation) {
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
        Matrix matrix = new Matrix();
        if (facing == Facing.FRONT)
            matrix.preScale(1.0f, -1.0f);
        matrix.postRotate(exifRotation);
        Bitmap bmp2 = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp2.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }

    @Override
    protected void dispatchResult() {
        LOG.i("dispatching result. Thread:", Thread.currentThread());
        super.dispatchResult();
    }
}
