import com.pi4j.component.motor.impl.GpioStepperMotorComponent;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ProcessServiceImple implements ProcessService {
    private ProcessMapperImple processMapperImple;

    private final static GpioController gpioController = GpioFactory.getInstance();

    private final static GpioPinDigitalOutput[][] PINS_MOTOR = {
            {
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_02, PinState.LOW),
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_03, PinState.LOW),
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_04, PinState.LOW),
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_05, PinState.LOW)
            },

            {
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_22, PinState.LOW),
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_23, PinState.LOW),
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_24, PinState.LOW),
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_25, PinState.LOW)
            },

            {
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_26, PinState.LOW),
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_27, PinState.LOW),
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_28, PinState.LOW),
                    gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_29, PinState.LOW)
            }
    };

    private final static GpioPinDigitalInput PIN_SWITCH = gpioController.provisionDigitalInputPin(RaspiPin.GPIO_00, PinPullResistance.PULL_DOWN);
    private final static GpioPinDigitalInput PIN_LOADCELL_INPUT = gpioController.provisionDigitalInputPin(RaspiPin.GPIO_15, "HX_DAT", PinPullResistance.OFF);

    private final static GpioPinDigitalOutput PIN_LED = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_01, PinState.LOW);
    private final static GpioPinDigitalOutput PIN_LOADCELL_OUTPUT = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_16, "HX_CLK", PinState.LOW);

    private static boolean switchStatus = false;

    public ProcessServiceImple(ProcessMapperImple processMapperImple) {
        this.processMapperImple = processMapperImple;
    }

    public static boolean getSwitchStatus() {
        return switchStatus;
    }

    public static void setSwitchStatus(boolean switchStatus) {
        ProcessServiceImple.switchStatus = switchStatus;
    }

    public static GpioController getGpioController() {
        return gpioController;
    }

    @Override
    public List<Map<String, String>> textMapping() {
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
                pumpInfo.put(readTexts[i].split("[:]")[0], readTexts[i].split("[:]")[1]);
                pumpInfos.add(pumpInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pumpInfos;
    }

    @Override
    public void executeManufacture(InputInfo inputInfo) throws Exception {
        controlPump(inputInfo);

        Thread.sleep(5000);

        int productWeight = measureProductWeight();

        Thread.sleep(1000);

        controlLED(true);
        Thread.sleep(500);

        while (true) {
            if (!(viewContactSwitch())) {
                break;
            }
            Thread.sleep(500);
        }

        Map<String, Integer> product = new HashMap<String, Integer>();
        product.put("productWeight", productWeight);
        processMapperImple.sendProductInfo(product);
        Thread.sleep(500);

        controlLED(false);
    }

    @Override
    public boolean viewContactSwitch() {
        try {
            PIN_SWITCH.addListener(new GpioPinListenerDigital() {
                @Override
                public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
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

    @Override
    public void controlPump(InputInfo inputInfo) throws Exception {
        final GpioStepperMotorComponent[] motors = {new GpioStepperMotorComponent(PINS_MOTOR[0])
                , new GpioStepperMotorComponent(PINS_MOTOR[1])
                , new GpioStepperMotorComponent(PINS_MOTOR[2])};

        final byte[] double_step_sequence = new byte[4];
        double_step_sequence[0] = (byte) 0b0011;
        double_step_sequence[1] = (byte) 0b0110;
        double_step_sequence[2] = (byte) 0b1100;
        double_step_sequence[3] = (byte) 0b1001;

        List<Map<String, String>> pumpInfo = inputInfo.getPumpInfo();

        for (int i = 0; i < motors.length; i++) {
            motors[i].setStepInterval(2);
            motors[i].setStepSequence(double_step_sequence);
        }

        for (int i = 0; i < pumpInfo.size(); i++) {
            Iterator<String> pump = pumpInfo.get(i).keySet().iterator();
            String pumpNo = pump.next();

            motors[(Integer.valueOf(pumpNo) - 1)].step(-509);
            Thread.sleep(Long.valueOf(pumpInfo.get(i).get(pumpNo)) / 10);

            motors[(Integer.valueOf(pumpNo) - 1)].step(509);
            Thread.sleep(1000);
        }
    }

    @Override
    public void controlLED(boolean status) {
        try {
            if (status) {
                // LED점등
                PIN_LED.high();
            } else {
                // LED소등
                PIN_LED.low();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int measureProductWeight() {
        //로드셀 객체 생성
        Hx711 hx711 = new Hx711(gpioController.provisionDigitalInputPin(RaspiPin.GPIO_15), gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_16), 5000, 2.0, GainFactor.GAIN_128);
        hx711.measureAndSetTare();
        //무게 값 측정
        long totalValue = 0;
        for (int i = 0; i < 5; i++) {
            totalValue += hx711.measure();
        }

        return (int) ((totalValue / 5) / 1000);
    }
}