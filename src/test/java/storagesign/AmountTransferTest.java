package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AmountTransferTest {

    @Test
    void acceptsOnlyRemainingIntegerCapacity() {
        assertEquals(64, AmountTransfer.accepted(100, 64));
        assertEquals(2, AmountTransfer.accepted(Integer.MAX_VALUE - 2, 64));
        assertEquals(0, AmountTransfer.accepted(Integer.MAX_VALUE, 1));
        assertEquals(0, AmountTransfer.accepted(0, -1));
        assertEquals(0, AmountTransfer.accepted(-1, 64));
        assertEquals(0, AmountTransfer.accepted(10, 0));
    }

    @Test
    void dividesEvenlyAndLeavesOneShareInBlock() {
        assertEquals(50, AmountTransfer.dividedPerSign(100, 1, 345600));
        assertEquals(33, AmountTransfer.dividedPerSign(100, 2, 345600));
    }

    @Test
    void appliesNormalAndSneakLimitsPerEmptySign() {
        assertEquals(10, AmountTransfer.dividedPerSign(100, 2, 10));
        assertEquals(5, AmountTransfer.dividedPerSign(100, 2, 5));
        assertEquals(33, AmountTransfer.dividedPerSign(100, 2, 0));
        assertEquals(33, AmountTransfer.dividedPerSign(100, 2, -1));
    }

    @Test
    void handlesBoundariesWithoutOverflow() {
        assertEquals(0, AmountTransfer.dividedPerSign(2, 2, Integer.MAX_VALUE));
        assertEquals(1, AmountTransfer.dividedPerSign(3, 2, Integer.MAX_VALUE));
        assertEquals(715827882,
            AmountTransfer.dividedPerSign(Integer.MAX_VALUE, 2, Integer.MAX_VALUE));
        assertEquals(345600,
            AmountTransfer.dividedPerSign(Integer.MAX_VALUE, 16, 345600));
        assertEquals(0, AmountTransfer.dividedPerSign(0, 1, 10));
        assertEquals(0, AmountTransfer.dividedPerSign(10, 0, 10));
        assertEquals(0, AmountTransfer.dividedPerSign(1, 2, 10));
    }

    @Test
    void dividedPerSignReturnsTheComputedShareWhenOneExists() {
        assertEquals(50, AmountTransfer.dividedPerSign(100, 1, 1000));
        assertEquals(20, AmountTransfer.dividedPerSign(60, 2, 1000));
    }
}
