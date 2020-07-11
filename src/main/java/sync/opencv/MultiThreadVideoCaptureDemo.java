package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.yaml.snakeyaml.Yaml;
import sync.opencv.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class MultiThreadVideoCaptureDemo {

    private static final int DEFAULT_FRAMES_DELAY = 5;

    public static void main(String[] args) {

        Queue<Mat[]> rowFramesQueue = new ConcurrentLinkedQueue<>();
        Queue<Mat[]> processedFramesQueue = new ConcurrentLinkedQueue<>();
        Config config = new Config();

        // Load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        Yaml yaml = new Yaml();
        try {
            try (InputStream in = MultiThreadVideoCaptureDemo.class.getClassLoader().getResourceAsStream("application.yml")) {
                config = yaml.loadAs(in, Config.class);
                log.debug(config.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        FpsMeter fpsMeter = new FpsMeter("show");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(rowFramesQueue,
//                "http://93.87.72.254:8090/mjpg/video.mjpg?COUNTER#.Wt9InV_SGHE.link",
//                "http://93.87.72.254:8090/mjpg/video.mjpg?COUNTER#.Wt9InV_SGHE.link");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(rowFramesQueue, fpsMeter,
//                "https://hls-stream.fever-screener.altoros.com/14/1/video/stream.m3u8",
//                "https://hls-stream.fever-screener.altoros.com/14/2/video/stream.m3u8");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(rowFramesQueue,
//                "rtsp://admin:123admin123@172.16.16.12:33380/cam/realmonitor?channel=1&subtype=0 ",
//                "rtsp://admin:123admin123@172.16.16.12:33380/cam/realmonitor?channel=2&subtype=0");

        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(config.getWatchBox(), rowFramesQueue,
                "rtsp://admin:123admin123@82.209.244.52:33554/cam/realmonitor?channel=1&subtype=0 ",
                "rtsp://admin:123admin123@82.209.244.52:33554/cam/realmonitor?channel=2&subtype=0");

        Thread videoCaptureThread = new Thread(cap);
        videoCaptureThread.start();

        FrameProcessor frameProcessor = new FrameProcessor(rowFramesQueue, processedFramesQueue);
        Thread frameProcessorThread = new Thread(frameProcessor);
        frameProcessorThread.start();

        double fps = 0;
        do {
            fps = cap.getFps();//*/6;
            sleep(10);
        } while (fps == 0);

        int key = 0;
        Mat[] frame;
        boolean watchBox = false;
        do {
            try {
                frame = processedFramesQueue.poll();
                if (frame == null) {
                    sleep((int) Math.round(1000 / fps));
                } else {

                    fpsMeter.measure();
//                    log.trace(String.format("processedFramesQueue %d (%.2f) ", processedFramesQueue.size(), fpsMeter.getFps()));

                    Mat multiFrame = concatenate(frame, 5. / 8, watchBox ? config.getWatchBox() : null);
                    HighGui.imshow("Cap", multiFrame);

                    key = HighGui.waitKey((int) Math.round(700 / fps));
                    if (key == 66) {
                        watchBox = !watchBox;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                key = 27;
            }

        } while (!cap.isStopped() && key != 27);

        cap.release();
        log.debug("captures released");
        frameProcessor.stop();
        log.debug("frame processor stopped");
        try {
            videoCaptureThread.join();
            frameProcessorThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        fpsMeter.summary();
        System.exit(0);
    }

    private static Mat concatenate(Mat[] frames, Double resize, Rect watchBox) {
        Mat dst = new Mat();
        List<Mat> resized = new ArrayList<>(frames.length);

        if(watchBox != null){
            Imgproc.rectangle(frames[0], watchBox, new Scalar(0, 0, 255)); //red
            Imgproc.rectangle(frames[1], watchBox, new Scalar(255, 0, 0)); //blue
        }

        if (resize != null) {
            Size sz = new Size(frames[0].width() * resize, frames[0].height() * resize);
            for (int i = 0; i < frames.length; i++) {
                resized.add(new Mat());
                Imgproc.resize(frames[i], resized.get(i), sz);
            }
        }

        Core.hconcat(resized, dst);

        return dst;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
