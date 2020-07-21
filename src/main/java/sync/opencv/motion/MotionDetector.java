package sync.opencv.motion;

import org.opencv.core.Mat;

public interface MotionDetector {

    RectTreck detect(Mat image);

}
