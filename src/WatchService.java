import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.RaspiPin;

import java.nio.file.*;
import java.util.*;

public class WatchService {
    private ProcessServiceImple processServiceImple;
    private ProcessMapperImple processMapperImple;

    public WatchService(ProcessServiceImple processServiceImple, ProcessMapperImple processMapperImple) {
        this.processServiceImple = processServiceImple;
        this.processMapperImple = processMapperImple;
    }

    public static void main(String[] args) {
        ProcessMapperImple processMapperImple = new ProcessMapperImple();
        WatchService watchService = new WatchService(new ProcessServiceImple(processMapperImple), processMapperImple);
        watchService.watchService();
    }

    public void watchService() {
        try {
            java.nio.file.WatchService watchService = FileSystems.getDefault().newWatchService();

            Path path = Paths.get("/home/pi/led");
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) {
                WatchKey watchKey = watchService.take();
                List<WatchEvent<?>> events = watchKey.pollEvents();
                for (WatchEvent<?> event : events) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path context = (Path) event.context();

                    if ("ENTRY_MODIFY".equals(kind.toString())) {
                        if (context.getFileName().toString().indexOf("led.txt") != -1) {
                            InputInfo inputInfo = new InputInfo();
                            inputInfo.setPumpInfo(processServiceImple.textMapping());

                            processServiceImple.executeManufacture(inputInfo);
                        }
                    }
                }
                if (!watchKey.reset()) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ProcessServiceImple.getGpioController().shutdown();
        }
    }
}
