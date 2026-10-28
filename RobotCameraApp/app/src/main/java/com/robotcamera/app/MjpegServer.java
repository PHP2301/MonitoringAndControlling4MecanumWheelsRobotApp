package com.robotcamera.app;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Size;
import androidx.camera.core.ImageProxy;
import fi.iki.elonen.NanoHTTPD;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * MJPEG HTTP Server Siêu Tốc (Zero-Latency / Anti-Buffer-Bloat) cho Robot Camera
 */
public class MjpegServer extends NanoHTTPD {

    public static class FramePacket {
        public final byte[] bytes;
        public final long timestamp;

        public FramePacket(byte[] bytes, long timestamp) {
            this.bytes = bytes;
            this.timestamp = timestamp;
        }
    }

    public interface CommandListener {
        boolean onToggleTorch();
        void onSwitchCamera();
        void onSetZoom(float ratio);
        void onTakePhoto();
        void onStartRecord();
        void onStopRecord();
    }

    public interface OnFrameBitmapListener {
        void onFrameBitmap(Bitmap bitmap);
    }

    public CommandListener commandListener;
    private OnFrameBitmapListener onFrameBitmapListener;

    public void setCommandListener(CommandListener listener) {
        this.commandListener = listener;
    }

    public CommandListener getCommandListener() {
        return commandListener;
    }

    public void setOnFrameBitmap(OnFrameBitmapListener listener) {
        this.onFrameBitmapListener = listener;
    }

    public OnFrameBitmapListener getOnFrameBitmap() {
        return onFrameBitmapListener;
    }

    private volatile byte[] latestFrame;

    private final CopyOnWriteArrayList<ArrayBlockingQueue<FramePacket>> clientQueues = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ZeroLatencyMjpegInputStream> activeStreams = new CopyOnWriteArrayList<>();

    public int jpegQuality = 50;
    public Size targetOutputResolution = null;

    private final ByteArrayOutputStream reusableBaos = new ByteArrayOutputStream(64 * 1024);

    private int frameCount = 0;
    private long lastFpsTime = System.currentTimeMillis();
    public volatile int currentFps = 0;

    public int getCurrentFps() {
        return currentFps;
    }

    public MjpegServer() {
        this(8080);
    }

    public MjpegServer(int port) {
        super(port);
    }

    @Override
    protected ClientHandler createClientHandler(final Socket finalAccept, final InputStream inputStream) {
        try {
            finalAccept.setTcpNoDelay(true);
            finalAccept.setSendBufferSize(64 * 1024);
            finalAccept.setReceiveBufferSize(32 * 1024);
            if (finalAccept.getInetAddress() != null && finalAccept.getInetAddress().getHostAddress() != null) {
                ButtonSyncManager.targetUnicastIp = finalAccept.getInetAddress().getHostAddress();
            }
        } catch (Exception ignored) {}
        return super.createClientHandler(finalAccept, inputStream);
    }

    public void pushFrame(ImageProxy image) {
        try {
            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            Bitmap bitmap = image.toBitmap();

            Bitmap finalBitmap;
            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate((float) rotationDegrees);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                }
                finalBitmap = rotated;
            } else {
                finalBitmap = bitmap;
            }

            boolean isLandscape = finalBitmap.getWidth() >= finalBitmap.getHeight();
            float currentRatio = (float) finalBitmap.getWidth() / (float) finalBitmap.getHeight();

            if (isLandscape && currentRatio < 1.65f) {
                int targetHeight = (finalBitmap.getWidth() * 9) / 16;
                if (targetHeight < finalBitmap.getHeight()) {
                    int cropY = (finalBitmap.getHeight() - targetHeight) / 2;
                    Bitmap cropped = Bitmap.createBitmap(finalBitmap, 0, cropY, finalBitmap.getWidth(), targetHeight);
                    if (cropped != finalBitmap) {
                        finalBitmap.recycle();
                        finalBitmap = cropped;
                    }
                }
            } else if (!isLandscape && currentRatio > 0.62f) {
                int targetWidth = (finalBitmap.getHeight() * 9) / 16;
                if (targetWidth < finalBitmap.getWidth()) {
                    int cropX = (finalBitmap.getWidth() - targetWidth) / 2;
                    Bitmap cropped = Bitmap.createBitmap(finalBitmap, cropX, 0, targetWidth, finalBitmap.getHeight());
                    if (cropped != finalBitmap) {
                        finalBitmap.recycle();
                        finalBitmap = cropped;
                    }
                }
            }

            Size targetRes = targetOutputResolution;
            if (targetRes != null) {
                int targetW = isLandscape ? targetRes.getWidth() : targetRes.getHeight();
                int targetH = isLandscape ? targetRes.getHeight() : targetRes.getWidth();
                if (finalBitmap.getWidth() > targetW || finalBitmap.getHeight() > targetH) {
                    Bitmap scaled = Bitmap.createScaledBitmap(finalBitmap, targetW, targetH, true);
                    if (scaled != finalBitmap) {
                        finalBitmap.recycle();
                        finalBitmap = scaled;
                    }
                }
            }

            try {
                if (onFrameBitmapListener != null) {
                    onFrameBitmapListener.onFrameBitmap(finalBitmap);
                }
            } catch (Exception ignored) {}

            byte[] jpegBytes;
            synchronized (reusableBaos) {
                reusableBaos.reset();
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, reusableBaos);
                jpegBytes = reusableBaos.toByteArray();
            }

            latestFrame = jpegBytes;
            long now = System.currentTimeMillis();
            FramePacket packet = new FramePacket(jpegBytes, now);

            frameCount++;
            if (now - lastFpsTime >= 1000) {
                currentFps = frameCount;
                frameCount = 0;
                lastFpsTime = now;
            }

            for (ArrayBlockingQueue<FramePacket> queue : clientQueues) {
                while (queue.remainingCapacity() == 0) {
                    queue.poll();
                }
                queue.offer(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            image.close();
        }
    }

    public int getClientCount() {
        return clientQueues.size();
    }

    public byte[] getLatestSnapshot() {
        return latestFrame;
    }

    public void pushJpegBytes(byte[] jpegBytes) {
        try {
            latestFrame = jpegBytes;
            long now = System.currentTimeMillis();
            FramePacket packet = new FramePacket(jpegBytes, now);

            frameCount++;
            if (now - lastFpsTime >= 1000) {
                currentFps = frameCount;
                frameCount = 0;
                lastFpsTime = now;
            }

            for (ArrayBlockingQueue<FramePacket> queue : clientQueues) {
                while (queue.remainingCapacity() == 0) {
                    queue.poll();
                }
                queue.offer(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        if ("/video".equals(uri) || "/video.mjpeg".equals(uri) || "/stream.mjpeg".equals(uri) || "/".equals(uri)) {
            return serveZeroLatencyMjpegStream();
        }

        if ("/shot.jpg".equals(uri) || "/photo.jpg".equals(uri)) {
            return serveSnapshot();
        }

        if ("/status".equals(uri)) {
            return newFixedLengthResponse(
                Response.Status.OK, "application/json",
                "{\"app\":\"RobotCameraApp\",\"status\":\"streaming\",\"fps\":" + currentFps + ",\"clients\":" + clientQueues.size() + ",\"quality\":" + jpegQuality + "}"
            );
        }

        if ("/torch".equals(uri)) {
            boolean on = (commandListener != null) && commandListener.onToggleTorch();
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"torch\":" + on + "}");
        }

        if ("/switch".equals(uri)) {
            if (commandListener != null) commandListener.onSwitchCamera();
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"result\":\"switched\"}");
        }

        if ("/zoom".equals(uri)) {
            float ratio = 1.0f;
            if (params.containsKey("ratio")) {
                try {
                    ratio = Float.parseFloat(params.get("ratio"));
                } catch (Exception ignored) {}
            }
            if (commandListener != null) commandListener.onSetZoom(ratio);
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"zoom\":" + ratio + "}");
        }

        if ("/snap".equals(uri)) {
            if (commandListener != null) commandListener.onTakePhoto();
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"result\":\"captured\"}");
        }

        if ("/record/start".equals(uri)) {
            if (commandListener != null) commandListener.onStartRecord();
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"result\":\"recording\"}");
        }

        if ("/record/stop".equals(uri)) {
            if (commandListener != null) commandListener.onStopRecord();
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"result\":\"stopped\"}");
        }

        if ("/gimbal/move".equals(uri)) {
            float pan = 0f;
            float tilt = 0f;
            try {
                if (params.containsKey("pan")) pan = Float.parseFloat(params.get("pan"));
                if (params.containsKey("tilt")) tilt = Float.parseFloat(params.get("tilt"));
            } catch (Exception ignored) {}
            GimbalBleManager.INSTANCE.setPanTilt(pan, tilt);
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"gimbal\":\"move\",\"pan\":" + pan + ",\"tilt\":" + tilt + "}");
        }

        if ("/gimbal/recenter".equals(uri)) {
            GimbalBleManager.INSTANCE.recenter();
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"gimbal\":\"recenter\"}");
        }

        if ("/gimbal/mode".equals(uri)) {
            String modeStr = params.get("m");
            GimbalBleManager.FollowMode mode = (modeStr != null && !modeStr.isEmpty())
                ? GimbalBleManager.FollowMode.Companion.fromLabel(modeStr)
                : GimbalBleManager.INSTANCE.getCurrentFollowMode().next();
            GimbalBleManager.INSTANCE.setFollowMode(mode);
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"gimbal\":\"mode\",\"mode\":\"" + mode.getLabel() + "\"}");
        }

        if ("/gimbal/status".equals(uri)) {
            return newFixedLengthResponse(
                Response.Status.OK, "application/json",
                "{\"connected\":" + GimbalBleManager.INSTANCE.isConnected() + ",\"device\":\"" + GimbalBleManager.INSTANCE.getConnectedDeviceName() + "\",\"mode\":\"" + GimbalBleManager.INSTANCE.getCurrentFollowMode().getLabel() + "\"}"
            );
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }

    private Response serveZeroLatencyMjpegStream() {
        final ArrayBlockingQueue<FramePacket> clientQueue = new ArrayBlockingQueue<>(1);
        clientQueues.add(clientQueue);

        byte[] currentLatest = latestFrame;
        if (currentLatest != null) {
            clientQueue.offer(new FramePacket(currentLatest, System.currentTimeMillis()));
        }

        final ZeroLatencyMjpegInputStream[] streamRef = new ZeroLatencyMjpegInputStream[1];
        ZeroLatencyMjpegInputStream stream = new ZeroLatencyMjpegInputStream(clientQueue, () -> {
            clientQueues.remove(clientQueue);
            if (streamRef[0] != null) {
                activeStreams.remove(streamRef[0]);
            }
        });
        streamRef[0] = stream;
        activeStreams.add(stream);

        Response response = newChunkedResponse(
            Response.Status.OK,
            "multipart/x-mixed-replace; boundary=frame",
            stream
        );
        response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate, pre-check=0, post-check=0, max-age=0");
        response.addHeader("Pragma", "no-cache");
        response.addHeader("Expires", "0");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Connection", "close");
        return response;
    }

    private Response serveSnapshot() {
        byte[] frame = latestFrame;
        if (frame != null) {
            return newFixedLengthResponse(
                Response.Status.OK, "image/jpeg",
                new ByteArrayInputStream(frame), (long) frame.length
            );
        } else {
            return newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT,
                "Camera chưa sẵn sàng"
            );
        }
    }

    public void cleanup() {
        for (ZeroLatencyMjpegInputStream s : activeStreams) {
            try { s.closeStream(); } catch (Exception ignored) {}
        }
        activeStreams.clear();
        clientQueues.clear();
        latestFrame = null;
    }

    @Override
    public void stop() {
        cleanup();
        super.stop();
    }

    private class ZeroLatencyMjpegInputStream extends InputStream {
        private final ArrayBlockingQueue<FramePacket> queue;
        private final Runnable onCloseCallback;
        private byte[] currentBuffer;
        private int bufferPos = 0;
        private volatile boolean isClosed = false;

        public ZeroLatencyMjpegInputStream(ArrayBlockingQueue<FramePacket> queue, Runnable onCloseCallback) {
            this.queue = queue;
            this.onCloseCallback = onCloseCallback;
        }

        public void closeStream() {
            isClosed = true;
            queue.clear();
        }

        @Override
        public int read() {
            if (isClosed) return -1;
            if (!prepareBuffer()) return -1;
            return currentBuffer[bufferPos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (isClosed) return -1;
            if (!prepareBuffer()) return -1;
            int available = currentBuffer.length - bufferPos;
            int toRead = Math.min(len, available);
            System.arraycopy(currentBuffer, bufferPos, b, off, toRead);
            bufferPos += toRead;
            return toRead;
        }

        private boolean prepareBuffer() {
            while (!isClosed && (currentBuffer == null || bufferPos >= currentBuffer.length)) {
                try {
                    if (isClosed) return false;
                    FramePacket packet = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (isClosed) return false;
                    if (packet == null) {
                        byte[] fallback = latestFrame;
                        if (fallback != null && !isClosed) {
                            packet = new FramePacket(fallback, System.currentTimeMillis());
                        } else {
                            if (isClosed) return false;
                            continue;
                        }
                    }

                    while (!queue.isEmpty()) {
                        FramePacket next = queue.poll();
                        if (next != null) packet = next;
                    }

                    byte[] nextFrame = packet.bytes;
                    String header = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: " + nextFrame.length + "\r\n\r\n";
                    byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
                    byte[] footerBytes = "\r\n".getBytes(StandardCharsets.US_ASCII);

                    byte[] packetBytes = new byte[headerBytes.length + nextFrame.length + footerBytes.length];
                    System.arraycopy(headerBytes, 0, packetBytes, 0, headerBytes.length);
                    System.arraycopy(nextFrame, 0, packetBytes, headerBytes.length, nextFrame.length);
                    System.arraycopy(footerBytes, 0, packetBytes, headerBytes.length + nextFrame.length, footerBytes.length);

                    currentBuffer = packetBytes;
                    bufferPos = 0;
                } catch (InterruptedException e) {
                    return false;
                }
            }
            return !isClosed;
        }

        @Override
        public void close() {
            isClosed = true;
            try {
                super.close();
            } catch (Exception ignored) {}
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        }
    }
}
