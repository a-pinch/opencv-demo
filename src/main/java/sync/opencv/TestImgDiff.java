package sync.opencv;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import static org.opencv.imgcodecs.Imgcodecs.IMREAD_GRAYSCALE;

public class TestImgDiff {

    public static void main(String[] args) {

    // Load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);


        Mat src1 = Imgcodecs.imread(args[0], IMREAD_GRAYSCALE);
        Mat src2 = Imgcodecs.imread(args[1], IMREAD_GRAYSCALE);

        Imgproc.threshold(src1, src1, 150, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(src2, src2, 150, 255, Imgproc.THRESH_BINARY);

        Mat src, tmp;
        Mat diff = new Mat();
        Mat minDst = new Mat();
        Mat maxDst = new Mat();
        int d, maxD=0, minD=0;
        if(src1.width()>=src2.width()&&src1.height()>=src2.height()){
            src = src1.clone(); tmp = src2.clone();
        } else if(src1.width()<=src2.width()&&src1.height()<=src2.height()){
            src = src2.clone(); tmp = src1.clone();
        }else{
            return;
        }
        System.out.println(src.size()+"; "+tmp.size());
        for(int i=0; i<src.width()-tmp.width(); i++){
            for(int j=0; j<src.height()-tmp.height(); j++){
                Mat s = src.submat(new Rect(i,j,tmp.width(),tmp.height()));
                Core.absdiff(s, tmp, diff);
                d = Core.countNonZero(diff);
               Imgcodecs.imwrite("img_t_"+i+"_"+j+"_"+d+".png", diff);
                if(minD == 0 || d<minD){
                    minD = d;
                    diff.copyTo(minDst);
                }else if(d>maxD){
                    maxD = d;
                    diff.copyTo(maxDst);
                }
                System.out.println(i+","+j+"; "+d);
            }
        }
        Imgcodecs.imwrite("img_min_"+minD+".png", minDst);
    }
}
