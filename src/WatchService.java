import com.pi4j.component.motor.impl.GpioStepperMotorComponent;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
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

                            //스위치가 눌려있다면 생산 실행 (배합물 통이 위치했을 경우를 말한다)
                            while (!(processServiceImple.viewContactSwitch())) {
                                if (ProcessServiceImple.getSwitchStatus()) {
                                    break;
                                } else {
                                    processServiceImple.controlLED(true);
                                    Thread.sleep(500);
                                    processServiceImple.controlLED(false);
                                    Thread.sleep(500);
                                    continue;
                                }
                            }
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
