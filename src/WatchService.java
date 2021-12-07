import java.nio.file.*;
import java.util.*;

public class WatchService {
    private ProcessServiceImple processServiceImple;

    public WatchService(ProcessServiceImple processServiceImple) {
        this.processServiceImple = processServiceImple;
    }

    public static void main(String[] args) {
        WatchService watchService = new WatchService(new ProcessServiceImple(new ProcessMapperImple()));
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
        } catch (NumberFormatException ne) {
            try {
                Map<String, Integer> response = new HashMap<String, Integer>();
                response.put("productWeight", 0);
                response.put("code", 102);

                processServiceImple.getProcessMapperImple().sendProductInfo(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ProcessServiceImple.getGpioController().shutdown();
        }
    }
}
