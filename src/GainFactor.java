public enum GainFactor {
    GAIN_128(24),
    GAIN_64(26),
    // 32-bit is for channel-B of hx711 module
    GAIN_32(25);

    private int gain;

    GainFactor(int gain) {
        this.gain = gain;
    }

    public int getGain() {
        return gain;
    }
}
