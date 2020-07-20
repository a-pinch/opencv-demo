package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@RestController
@Slf4j
public class VideoStreamController {

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name="name", required=false, defaultValue="World") String name) {
        return "Hello "+ name + "; " + MultiThreadVideoCaptureDemo.getQueueSize();
    }

    @GetMapping(value = "/stream")
    public ResponseEntity<StreamingResponseBody> stream(final HttpServletResponse response) throws IOException {
        response.setContentType("multipart/x-mixed-replace; boundary=frame");
        boolean b = true;
        StreamingResponseBody stream = out -> {
            ServletOutputStream outputStream = response.getOutputStream();
            while(b){
                Mat[] mats = MultiThreadVideoCaptureDemo.get();
                if (mats != null) {
                    MatOfByte buf = new MatOfByte();
                    if(Imgcodecs.imencode(".jpg", mats[0], buf)) {
                        outputStream.write("--frame\r\nContent-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                        outputStream.write(buf.toArray());
                        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                        log.trace("wrote " + buf.size() + " bites (" + MultiThreadVideoCaptureDemo.getQueueSize() + ")");
                    }else{
                        log.warn("failed to encode jpg");
                    }
                }
//                try {
//                    Thread.sleep(1000/8);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
            }
            log.info("exit from web streaming controller");
            outputStream.close();
        };

//        return "Hello "+ name + "; " + MultiThreadVideoCaptureDemo.getQueueSize();
        log.info("steaming response {} ", stream);
        return new ResponseEntity(stream, HttpStatus.OK);
    }
}
