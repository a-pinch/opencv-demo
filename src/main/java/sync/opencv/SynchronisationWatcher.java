package sync.opencv;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class SynchronisationWatcher {

    private Rect watcher;

    public SynchronisationWatcher(Rect watcher) {
        this.watcher = watcher;
    }

    public void red(Mat[] frames){
        Imgproc.rectangle(frames[0], watcher, new Scalar(0, 0, 255));
        Imgproc.rectangle(frames[1], watcher, new Scalar(255, 0, 0));
    }
}
