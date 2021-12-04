import com.pi4j.io.gpio.*;

public class Hx711 {


    private final GpioPinDigitalOutput pinSCK;
    private final GpioPinDigitalInput pinDT;
    private int gain;
    private final int loadCellMaxWeight;
    private double ratedOutput;
    private long tareOffset;

    /**
     * hx711 센서의 인스턴스를 생성합니다. 생성자에서 저울의 무게를 잽니다.
     *
     * @param pinDT             DT 와이어용 GPIO 핀.
     * @param pinSCK            와이어용 SCK GPIO 핀.
     * @param loadCellMaxWeight 로드셀의 최대 무게(g).
     * @param ratedOutput       로드셀의 정격 출력(mv/V).
     *                          예 값 '1.0' 또는 '2.0'이 일반적입니다.
     * @param gainFactor        128비트는 공통입니다.
     */
    public Hx711(GpioPinDigitalInput pinDT, GpioPinDigitalOutput pinSCK, int loadCellMaxWeight,
                 double ratedOutput, GainFactor gainFactor) {
        this.pinSCK = pinSCK;
        this.pinDT = pinDT;
        this.loadCellMaxWeight = loadCellMaxWeight;
        this.ratedOutput = ratedOutput;
        this.gain = gainFactor.getGain();
        pinSCK.setState(PinState.LOW);
    }

    public static void main(String[] args) {
        GpioController gpioController = GpioFactory.getInstance();

        Hx711 hx = new Hx711(gpioController.provisionDigitalInputPin(RaspiPin.GPIO_15), gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_16), 5000, 2.0, GainFactor.GAIN_128);
        hx.measureAndSetTare();
        while (true) {
            try {
                System.out.println("value : " + hx.measure());
                Thread.sleep(1500);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * 저울의 현재 부하를 측정합니다. (용기 오프셋은 차감됩니다)
     * 경고! 전에 용기 값을 설정했는지 확인하십시오. 그렇지 않으면 결과가 나옵니다.
     * 불량이 됩니다.
     *
     * @소수점 없이 반올림된 그램(g) 단위의 하중을 반환합니다.
     */
    public long measure() {

        double measuredKilogram = ((readValue() - tareOffset) * 0.5 * loadCellMaxWeight) / ((ratedOutput / 1000) * 128 * 8388608);
        double measuredGrams = measuredKilogram * 1000;
        long roundedGrams = Math.round(measuredGrams);

        return roundedGrams;
    }


    /**
     * 로드셀 센서에서 값을 가져옵니다.
     *
     * @return 로드셀 센서가 반환한 원시 디지털 값.
     */
    public long readValue() {

        pinSCK.setState(PinState.LOW);
        while (!isReadyForMeasurement()) {
            sleepSafe(1);
        }

        long count = 0;
        for (int i = 0; i < this.gain; i++) {
            pinSCK.setState(PinState.HIGH);
            count = count << 1;
            pinSCK.setState(PinState.LOW);
            if (pinDT.isHigh()) {
                count++;
            }
        }

        pinSCK.setState(PinState.HIGH);
        count = count ^ 0x800000;
        pinSCK.setState(PinState.LOW);

        System.out.println("Read value from sensor: " + count);

        return count;
    }

    /**
     * 로드셀의 현재값을 측정하여 용기값으로 설정합니다.
     * 경고: 이 방법을 실행하면 이에 대한 빈 값이 설정됩니다.
     * 로드 셀!
     *
     * @return
     */
    public long measureAndSetTare() {
        long tareValue = readValue();
        this.tareOffset = tareValue;

        return tareValue;
    }

    /**
     * 저울의 용기 값을 주어진 값으로 설정합니다.
     *
     * @param tareValue 값은 저울이 비어 있을 때 얻을 수 있습니다.
     */
    public void setTareValue(long tareValue) {
        this.tareOffset = tareValue;
    }

    private boolean isReadyForMeasurement() {
        return (pinDT.isLow());
    }

    private void sleepSafe(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            System.err.println(ex.getMessage());
        }
    }
}
