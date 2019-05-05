/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.numbers

import kotlin.test.*

class NumbersJVMTest {

    @Test
    fun floatToBits() {
        val PI_F = kotlin.math.PI.toFloat()
        assertEquals(0x40490fdb, PI_F.toBits())
        assertEquals(PI_F, Float.fromBits(0x40490fdb))

        for (value in listOf(Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, -1.0F, -Float.MIN_VALUE, -0.0F, 0.0F, Float.POSITIVE_INFINITY, Float.MAX_VALUE, 1.0F, Float.MIN_VALUE)) {
            assertEquals(value, Float.fromBits(value.toBits()))
            assertEquals(value, Float.fromBits(value.toRawBits()))
        }
        assertTrue(Float.NaN.toBits().let(Float.Companion::fromBits).isNaN())
        assertTrue(Float.NaN.toRawBits().let { Float.fromBits(it) }.isNaN())
    }


    @UseExperimental(ExperimentalStdlibApi::class)
    @Test
    fun byteBits() {
        fun test(value: Byte, oneBits: Int, leadingZeroes: Int, trailingZeroes: Int) {
            assertEquals(oneBits, value.countOneBits())
            assertEquals(leadingZeroes, value.countLeadingZeroBits())
            assertEquals(trailingZeroes, value.countTrailingZeroBits())
            val highestBit = if (leadingZeroes < Byte.SIZE_BITS) 1.shl(Byte.SIZE_BITS - leadingZeroes - 1).toByte() else 0
            val lowestBit = if (trailingZeroes < Byte.SIZE_BITS) 1.shl(trailingZeroes).toByte() else 0
            assertEquals(highestBit, value.takeHighestOneBit())
            assertEquals(lowestBit, value.takeLowestOneBit())
        }

        test(0, 0, 8, 8)
        test(1, 1, 7, 0)
        test(2, 1, 6, 1)
        test(0x44, 2, 1, 2)
        test(0x80.toByte(), 1, 0, 7)
        test(0xF0.toByte(), 4, 0, 4)
    }
    
    @UseExperimental(ExperimentalStdlibApi::class)
    @Test
    fun shortBits() {
        fun test(value: Short, oneBits: Int, leadingZeroes: Int, trailingZeroes: Int) {
            assertEquals(oneBits, value.countOneBits())
            assertEquals(leadingZeroes, value.countLeadingZeroBits())
            assertEquals(trailingZeroes, value.countTrailingZeroBits())
            val highestBit = if (leadingZeroes < Short.SIZE_BITS) 1.shl(Short.SIZE_BITS - leadingZeroes - 1).toShort() else 0
            val lowestBit = if (trailingZeroes < Short.SIZE_BITS) 1.shl(trailingZeroes).toShort() else 0
            assertEquals(highestBit, value.takeHighestOneBit())
            assertEquals(lowestBit, value.takeLowestOneBit())
        }

        test(0, 0, 16, 16)
        test(1, 1, 15, 0)
        test(2, 1, 14, 1)
        test(0xF2, 5, 8, 1)
        test(0x8000.toShort(), 1, 0, 15)
        test(0xF200.toShort(), 5, 0, 9)
    }

    @UseExperimental(ExperimentalStdlibApi::class)
    @Test
    fun intBits() {
        fun test(value: Int, oneBits: Int, leadingZeroes: Int, trailingZeroes: Int) {
            assertEquals(oneBits, value.countOneBits())
            assertEquals(leadingZeroes, value.countLeadingZeroBits())
            assertEquals(trailingZeroes, value.countTrailingZeroBits())
            val highestBit = if (leadingZeroes < Int.SIZE_BITS) 1.shl(Int.SIZE_BITS - leadingZeroes - 1) else 0
            val lowestBit = if (trailingZeroes < Int.SIZE_BITS) 1.shl(trailingZeroes) else 0
            assertEquals(highestBit, value.takeHighestOneBit())
            assertEquals(lowestBit, value.takeLowestOneBit())
        }

        test(0, 0, 32, 32)
        test(1, 1, 31, 0)
        test(2, 1, 30, 1)
        test(0xF002, 5, 16, 1)
        test(0xF00F0000.toInt(), 8, 0, 16)
    }

    @UseExperimental(ExperimentalStdlibApi::class)
    @Test
    fun longBits() {
        fun test(value: Long, oneBits: Int, leadingZeroes: Int, trailingZeroes: Int) {
            assertEquals(oneBits, value.countOneBits())
            assertEquals(leadingZeroes, value.countLeadingZeroBits())
            assertEquals(trailingZeroes, value.countTrailingZeroBits())
            val highestBit = if (leadingZeroes < Long.SIZE_BITS) 1L.shl(Long.SIZE_BITS - leadingZeroes - 1).toLong() else 0
            val lowestBit = if (trailingZeroes < Long.SIZE_BITS) 1L.shl(trailingZeroes).toLong() else 0
            assertEquals(highestBit, value.takeHighestOneBit())
            assertEquals(lowestBit, value.takeLowestOneBit())
        }

        test(0, 0, 64, 64)
        test(1, 1, 63, 0)
        test(2, 1, 62, 1)
        test(0xF002, 5, 48, 1)
        test(0xF00F0000L, 8, 32, 16)
        test(0x1111_3333_EEEE_0000L, 4 + 8 + 12, 3, 17)
    }

}