package sync.opencv;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import static org.opencv.imgcodecs.Imgcodecs.IMREAD_GRAYSCALE;

public class TestImgDiff2 {

    public static void main(String[] args) {

    // Load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);


        Mat src1 = Imgcodecs.imread(args[0], IMREAD_GRAYSCALE);
        Mat src2 = Imgcodecs.imread(args[1], IMREAD_GRAYSCALE);

        Imgproc.threshold(src1, src1, 150, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(src2, src2, 150, 255, Imgproc.THRESH_BINARY);

        Mat diff = new Mat(), minDst = new Mat();
        int d, minD=-1;

        for(int w=0; w<Math.abs(src1.width()-src2.width()); w++){
            for(int h=0; h<Math.abs(src1.height()-src2.height()); h++){
                Mat s1 = src1.submat(new Rect(src1.width()>src2.width()?w:0,
                        src1.height()>src2.height()?h:0,
                        Math.min(src1.width(), src2.width()),
                        Math.min(src1.height(), src2.height())));
                Mat s2 = src2.submat(new Rect(src2.width()>src1.width()?w:0,
                        src2.height()>src1.height()?h:0,
                        Math.min(src1.width(), src2.width()),
                        Math.min(src1.height(), src2.height())));
                Core.absdiff(s1, s2, diff);
                d = Core.countNonZero(diff);
                Imgcodecs.imwrite("img2_t_"+w+"_"+h+"_"+d+".png", diff);
                if(minD < 0 || d<minD){
                    minD = d;
                    diff.copyTo(minDst);
                }
            }
        }
        Imgcodecs.imwrite("img2_min_"+minD+".png", minDst);
    }
}
