import com.pi4j.component.motor.impl.GpioStepperMotorComponent;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.impl.GpioControllerImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ProcessServiceImple implements ProcessService {
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

    private final static GpioPinDigitalOutput PIN_LED = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_01, PinState.LOW);

    private final Hx711 hx711 = new Hx711(gpioController.provisionDigitalInputPin(RaspiPin.GPIO_15),
            gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_16), 5000, 1.0, GainFactor.GAIN_128);
    private static boolean contactSwitchStatus = false;

    public ProcessServiceImple(ProcessMapperImple processMapperImple) {
        this.processMapperImple = processMapperImple;
    }

    public static boolean getContactSwitchStatus() {
        return contactSwitchStatus;
    }

    public static void setContactSwitchStatus(boolean contactSwitchStatus) {
        ProcessServiceImple.contactSwitchStatus = contactSwitchStatus;
    }

    public static GpioController getGpioController() {
        return gpioController;
    }

    private ProcessMapperImple processMapperImple;

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
        int count = 0;
        while (true) {
            if (count == 60) {
                Map<String, Integer> product = new HashMap<String, Integer>();
                product.put("productWeight", 0);
                product.put("code", 101);
                processMapperImple.sendProductInfo(product);

                return;
            }

            if ((viewContactSwitch())) {
                break;

            } else {

                controlLED(true);
                Thread.sleep(500);

                controlLED(false);
                Thread.sleep(500);
                count++;

                continue;
            }
        }

        Thread.sleep(2000);
        hx711.measureAndSetTare();

        controlPump(inputInfo);
        Thread.sleep(5000);

        int productWeight = measureProductWeight(hx711);
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
        product.put("code", 200);

        processMapperImple.sendProductInfo(product);
        Thread.sleep(500);

        controlLED(false);
    }

    @Override
    public boolean viewContactSwitch() {
            PIN_SWITCH.addListener(new GpioPinListenerDigital() {
                @Override
                public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                    PinState pinState = event.getState();

                    if ("LOW".equals(pinState.toString())) {
                        setContactSwitchStatus(true);
                    } else {
                        setContactSwitchStatus(false);
                    }
                }
            });

        return getContactSwitchStatus();
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

        Long motorSecond = 0l;
        for (int i = 0; i < pumpInfo.size(); i++) {
            Iterator<String> pump = pumpInfo.get(i).keySet().iterator();
            String pumpNo = pump.next();

            if ((Integer.valueOf(pumpNo) == 1)) {
                motors[(Integer.valueOf(pumpNo) - 1)].step(103);

                motorSecond = (long) (((Double.valueOf(pumpInfo.get(i).get(pumpNo)) -7.6) / 20.80) * 1000);
                Thread.sleep(motorSecond);


                motors[(Integer.valueOf(pumpNo) - 1)].step(-103);
                Thread.sleep(1000);
            } else if ((Integer.valueOf(pumpNo) == 2)) {
                motors[(Integer.valueOf(pumpNo) - 1)].step(-140);

                motorSecond = (long) (((Double.valueOf(pumpInfo.get(i).get(pumpNo)) -7.6) / 16.40) * 1000);
                Thread.sleep(motorSecond);

                motors[(Integer.valueOf(pumpNo) - 1)].step(140);
                Thread.sleep(1000);
            } else if ((Integer.valueOf(pumpNo) == 3)) {
                motors[(Integer.valueOf(pumpNo) - 1)].step(-155);

                motorSecond = (long) (((Double.valueOf(pumpInfo.get(i).get(pumpNo)) -7.6) / 17.50) * 1000);
                Thread.sleep(motorSecond);

                motors[(Integer.valueOf(pumpNo) - 1)].step(155);
                Thread.sleep(1000);
            }
        }
    }

    @Override
    public void controlLED(boolean status) {
        if (status) {
            PIN_LED.high();
        } else {
            PIN_LED.low();
        }
    }

    @Override
    public int measureProductWeight(Hx711 hx711) throws Exception {
        long value = hx711.measure();

        return (int) (value * -1);
    }
}