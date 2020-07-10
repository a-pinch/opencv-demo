package sync.opencv.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.opencv.core.Rect;

@Getter @Setter @ToString
public class Config {
    Watcher watcher = new Watcher();
}
