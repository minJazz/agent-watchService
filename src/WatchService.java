import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.*;
import java.util.*;

public class WatchService {
    private static final Logger logger = LogManager.getLogger(WatchService.class);
    private ProcessServiceImple processServiceImple;

    public WatchService(ProcessServiceImple processServiceImple) {
        this.processServiceImple = processServiceImple;
    }

    public static void main(String[] args) {
        ProcessMapperImple processMapperImple = new ProcessMapperImple();
        WatchService watchService = new WatchService(new ProcessServiceImple(processMapperImple));
        watchService.watchService();
    }

    public void watchService() {
        try {
            java.nio.file.WatchService watchService = FileSystems.getDefault().newWatchService();

            Path path = Paths.get("/home/pi/process");
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) {
                WatchKey watchKey = watchService.take();

                Thread.sleep(50);

                List<WatchEvent<?>> events = watchKey.pollEvents();

                WatchEvent.Kind<?> kind = events.get(0).kind();
                Path context = (Path) events.get(0).context();

                if ("ENTRY_MODIFY".equals(kind.toString())) {
                    if (context.getFileName().toString().indexOf("process-info.txt") != -1) {
                        InputInfo inputInfo = new InputInfo();
                        inputInfo.setPumpInfo(processServiceImple.textMapping());

                        List<Map<String, String>> inputs = inputInfo.getPumpInfo();
                        int totalInputInfo = 0;
                        for (int i = 0; i < inputs.size(); i++) {
                            totalInputInfo += Integer.valueOf(inputs.get(i).get(String.valueOf(i + 1)));
                        }

                        logger.info("------------------------------");
                        logger.info("  《 Receive InputInfo 》 ");
                        logger.info("   Total InputInfo :" + totalInputInfo);
                        logger.info("   First Pump :" + inputs.get(0).get("1"));
                        logger.info("   Second Pump :" + inputs.get(1).get("2"));
                        logger.info("   Third Pump :" + inputs.get(2).get("3"));

                        processServiceImple.executeManufacture(inputInfo);


                    }
                }
                if (!watchKey.reset()) { break; }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ProcessServiceImple.getGpioController().shutdown();
        }
    }
}
