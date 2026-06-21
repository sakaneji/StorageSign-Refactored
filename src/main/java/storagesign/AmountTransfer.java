package storagesign;

/** StorageSign の数量移動をオーバーフローなしで計算する。 */
public final class AmountTransfer {

    private AmountTransfer() {}

    /** 現在量へ追加できる数量を返す。 */
    public static int accepted(int current, int requested) {
        if (current < 0 || requested <= 0) return 0;
        return (int) Math.min((long) requested, (long) Integer.MAX_VALUE - current);
    }

    /** 空 SS 一枚あたりへ割り当てる数量を返す。 */
    public static int dividedPerSign(int stored, int emptySigns, int limit) {
        if (stored <= 0 || emptySigns <= 0 || stored <= emptySigns) return 0;
        long evenShare = (long) stored / ((long) emptySigns + 1L);
        long perSign = limit > 0 ? Math.min(evenShare, (long) limit) : evenShare;
        return perSign > 0 ? (int) perSign : 0;
    }
}
