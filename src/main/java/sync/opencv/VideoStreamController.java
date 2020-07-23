package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@Slf4j
public class VideoStreamController {

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name="name", required=false, defaultValue="World") String name) {
        return "Hello "+ name + "; " + MultiThreadVideoCaptureDemo.getQueueSize();
    }

    @GetMapping(value = "/stream")
    public StreamingResponseBody stream(final HttpServletResponse response) throws IOException {
        response.setContentType("multipart/x-mixed-replace; boundary=frame");
        byte[] startFrame = "--frame\r\nContent-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] endFrame = "\r\n".getBytes(StandardCharsets.UTF_8);
        return out -> {
            ServletOutputStream outputStream = response.getOutputStream();
            while(true){
                byte[] mats = MultiThreadVideoCaptureDemo.get();

                    if (mats != null) {
                        outputStream.write(startFrame);
                        outputStream.write(mats);
                        outputStream.write(endFrame);
                        log.trace("wrote " + mats.length + " bites (" + MultiThreadVideoCaptureDemo.getQueueSize() + ")");
                    }
            }
        };
    }

    @GetMapping(value = "/fragment/{n}")
    public StreamingResponseBody fragment(@PathVariable int n, final HttpServletResponse response) throws IOException {
        response.setContentType("multipart/x-mixed-replace; boundary=frame");
        byte[] startFrame = "--frame\r\nContent-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] endFrame = "\r\n".getBytes(StandardCharsets.UTF_8);
        return out -> {
            ServletOutputStream outputStream = response.getOutputStream();
            while(true){
                byte[] mats = MultiThreadVideoCapture.getFragment(n);

                if (mats != null) {
                    outputStream.write(startFrame);
                    outputStream.write(mats);
                    outputStream.write(endFrame);
                }
            }
        };
    }

    @GetMapping(value = "/box")
    public boolean toggleWatchBox(){
        return MultiThreadVideoCaptureDemo.toggleWatchBox();
    }
}
