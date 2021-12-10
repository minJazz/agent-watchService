public enum GainFactor {
    GAIN_128(24),
    GAIN_64(26),
    GAIN_32(25);

    private int gain;

    GainFactor(int gain) {
        this.gain = gain;
    }

    public int getGain() {
        return gain;
    }
}
