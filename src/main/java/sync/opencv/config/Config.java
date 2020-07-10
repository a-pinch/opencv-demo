package sync.opencv.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.opencv.core.Rect;

@Getter @Setter @ToString
public class Config {
    Rect watcher = new Rect();
}
