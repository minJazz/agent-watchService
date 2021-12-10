import com.pi4j.io.gpio.*;

public class Hx711 {
    private final GpioPinDigitalOutput pinSCK;
    private final GpioPinDigitalInput pinDT;
    private int gain;
    private final int loadCellMaxWeight;
    private double ratedOutput;
    private long tareOffset;

    public Hx711(GpioPinDigitalInput pinDT, GpioPinDigitalOutput pinSCK, int loadCellMaxWeight,
                 double ratedOutput, GainFactor gainFactor) {
        this.pinSCK = pinSCK;
        this.pinDT = pinDT;
        this.loadCellMaxWeight = loadCellMaxWeight;
        this.ratedOutput = ratedOutput;
        this.gain = gainFactor.getGain();
        pinSCK.setState(PinState.LOW);
    }

    public long measure() {
        double measuredKilogram = ((readValue() - tareOffset) * 0.5 * loadCellMaxWeight) / ((ratedOutput / 1000) * 128 * 8388608);
        double measuredGrams = measuredKilogram * 1.0644;
        long roundedGrams = Math.round(measuredGrams);

        return roundedGrams;
    }

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

        return count;
    }

    public long measureAndSetTare() {
        long tareValue = readValue();
        this.tareOffset = tareValue;

        return tareValue;
    }

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
