import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WatchService {
    private final static GpioController gpioController = GpioFactory.getInstance();
    private final static Pin GPIO_IN_PIN_00 = RaspiPin.GPIO_00;
    private final static Pin GPIO_IN_PIN_15 = RaspiPin.GPIO_15;

    private final static Pin GPIO_OUT_PIN_01 = RaspiPin.GPIO_01;
    private final static Pin GPIO_OUT_PIN_16 = RaspiPin.GPIO_16;

    private final static GpioPinDigitalInput PIN_SWITCH = gpioController.provisionDigitalInputPin(GPIO_IN_PIN_00,
            PinPullResistance.PULL_DOWN);
    private final static GpioPinDigitalInput PIN_LOADCELL_INPUT = gpioController.provisionDigitalInputPin(GPIO_IN_PIN_15, "HX_DAT", PinPullResistance.OFF);

    private final static GpioPinDigitalOutput PIN_LED = gpioController.provisionDigitalOutputPin(GPIO_OUT_PIN_01, PinState.LOW);
    private final static GpioPinDigitalOutput PIN_LOADCELL_OUTPUT = gpioController.provisionDigitalOutputPin(GPIO_OUT_PIN_16, "HX_CLK", PinState.LOW);

    private static boolean switchStatus = false;

    public static void main(String[] args) {
        WatchService fileWatchService = new WatchService();

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
                            fileWatchService.flashLed();
                            InputInfo inputInfo = new InputInfo();
                            inputInfo.setPumpInfo(fileWatchService.textMapping());

                            //스위치가 눌려있다면 생산 실행 (배합물 통이 위치했을 경우를 말한다)
                            while (!(fileWatchService.viewContactSwitch())) {
                                Thread.sleep(700);
                                System.out.println(switchStatus);
                                if (switchStatus) {
                                    break;
                                } else {
                                    fileWatchService.flashLed();
                                    Thread.sleep(700);
                                    continue;
                                }
                            }
                            fileWatchService.executeManufacture(inputInfo);
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
            gpioController.shutdown();
        }
    }

    public List<Map<String, String>> textMapping () {
        //파일 내용 확인
        BufferedReader bufferedReader = null;
        StringBuffer readText = new StringBuffer();
        List<Map<String, String>> pumpInfos = null;

        try {
            File file = new File("/home/pi/led/led.txt");

            bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                readText.append(line);
            }

            String[] readTexts = readText.toString().split("[@]");

            pumpInfos = new ArrayList<>();
            for (int i = 0; i < readTexts.length; i++) {
                Map<String, String> pumpInfo = new HashMap<String, String>();
                System.out.println(readTexts[i]);
                pumpInfo.put(readTexts[i].split("[:]")[0],
                        readTexts[i].split("[:]")[1]);
                pumpInfos.add(pumpInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pumpInfos;
    }

    public void executeManufacture (InputInfo inputInfo) throws Exception {
        // TODO : 생산실행
        System.out.println(1);
        controlPump(inputInfo);

        Thread.sleep(10000);

        System.out.println(2);
        int productWeight = measureProductWeight();

        Thread.sleep(10000);

        System.out.println(3);
        onLed();
        Thread.sleep(10000);

        while (!(viewContactSwitch())) {
            Thread.sleep(1000);
        }

        System.out.println(5);

        Map<String, Integer> product = new HashMap<String, Integer>();
        product.put("productWeight", productWeight);
        //sendProductInfo(product);
        Thread.sleep(10000);
        offLed();
    }

    public boolean viewContactSwitch () {
        // TODO : 스위치 조작
        try {
            PIN_SWITCH.addListener(new GpioPinListenerDigital() {
                @Override
                public void handleGpioPinDigitalStateChangeEvent (
                        GpioPinDigitalStateChangeEvent event) {
                    PinState pinState = event.getState();

                    //스위치가 눌려 있는지? 눌렸다면 LOW 아닌경우 HIGH
                    if ("LOW".equals(pinState.toString())) {
                        switchStatus = true;
                    } else {
                        switchStatus = false;
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return switchStatus;
    }

    public void controlPump (InputInfo inputInfo) {
        // TODO : 원재료 펌프 제어
    }

    public void flashLed () {
        // TODO : LED점멸
        try {
            System.out.println("test");
            for (int i = 0; i < 3;  i++) {
                PIN_LED.low();
                Thread.sleep(500);

                PIN_LED.high();
                Thread.sleep(1000);

                PIN_LED.low();
                Thread.sleep(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onLed () {
        // TODO : LED점등
        try {
            PIN_LED.high();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void offLed () {
        // TODO : LED소등
        try {
            PIN_LED.low();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int measureProductWeight () {
        //로드셀 객체 생성
        Hx711 hx711 = new Hx711(PIN_LOADCELL_INPUT , PIN_LOADCELL_OUTPUT, 128);

        //무게 값 측정
        hx711.read();

        return (int)hx711.value;
    }

    public void sendProductInfo(Map<String, Integer> productInfo) {
        //172.16.30.115 상대 서버 측 IP
        String url = "http://192.168.0.152/product";
        StringBuffer body = new StringBuffer();
        body.append("{")
            .append("  \"productWeight\":"+productInfo.get("productWeight"))
            .append("}");

        OkHttpClient okHttpClient = new OkHttpClient();
        try {
            //post 요청을 위한 RequestBody 생성
            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json; " +
                            "charset=UTF-8"), body.toString());

            Request.Builder builder = new Request.Builder()
                                                 .url(url)
                                                 .post(requestBody);

            Request request = builder.build();

            Response response = okHttpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    System.out.println(responseBody.string());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
