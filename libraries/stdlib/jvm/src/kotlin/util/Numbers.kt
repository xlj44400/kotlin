/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("MathKt")
package kotlin

/**
 * Returns `true` if the specified number is a
 * Not-a-Number (NaN) value, `false` otherwise.
 */
@kotlin.internal.InlineOnly
public actual inline fun Double.isNaN(): Boolean = java.lang.Double.isNaN(this)

/**
 * Returns `true` if the specified number is a
 * Not-a-Number (NaN) value, `false` otherwise.
 */
@kotlin.internal.InlineOnly
public actual inline fun Float.isNaN(): Boolean = java.lang.Float.isNaN(this)

/**
 * Returns `true` if this value is infinitely large in magnitude.
 */
@kotlin.internal.InlineOnly
public actual inline fun Double.isInfinite(): Boolean = java.lang.Double.isInfinite(this)

/**
 * Returns `true` if this value is infinitely large in magnitude.
 */
@kotlin.internal.InlineOnly
public actual inline fun Float.isInfinite(): Boolean = java.lang.Float.isInfinite(this)

/**
 * Returns `true` if the argument is a finite floating-point value; returns `false` otherwise (for `NaN` and infinity arguments).
 */
@kotlin.internal.InlineOnly
public actual inline fun Double.isFinite(): Boolean = !isInfinite() && !isNaN()

/**
 * Returns `true` if the argument is a finite floating-point value; returns `false` otherwise (for `NaN` and infinity arguments).
 */
@kotlin.internal.InlineOnly
public actual inline fun Float.isFinite(): Boolean = !isInfinite() && !isNaN()

/**
 * Returns a bit representation of the specified floating-point value as [Long]
 * according to the IEEE 754 floating-point "double format" bit layout.
 */
@SinceKotlin("1.2")
@kotlin.internal.InlineOnly
public actual inline fun Double.toBits(): Long = java.lang.Double.doubleToLongBits(this)

/**
 * Returns a bit representation of the specified floating-point value as [Long]
 * according to the IEEE 754 floating-point "double format" bit layout,
 * preserving `NaN` values exact layout.
 */
@SinceKotlin("1.2")
@kotlin.internal.InlineOnly
public actual inline fun Double.toRawBits(): Long = java.lang.Double.doubleToRawLongBits(this)

/**
 * Returns the [Double] value corresponding to a given bit representation.
 */
@SinceKotlin("1.2")
@kotlin.internal.InlineOnly
public actual inline fun Double.Companion.fromBits(bits: Long): Double = java.lang.Double.longBitsToDouble(bits)

/**
 * Returns a bit representation of the specified floating-point value as [Int]
 * according to the IEEE 754 floating-point "single format" bit layout.
 */
@SinceKotlin("1.2")
@kotlin.internal.InlineOnly
public actual inline fun Float.toBits(): Int = java.lang.Float.floatToIntBits(this)

/**
 * Returns a bit representation of the specified floating-point value as [Int]
 * according to the IEEE 754 floating-point "single format" bit layout,
 * preserving `NaN` values exact layout.
 */
@SinceKotlin("1.2")
@kotlin.internal.InlineOnly
public actual inline fun Float.toRawBits(): Int = java.lang.Float.floatToRawIntBits(this)

/**
 * Returns the [Float] value corresponding to a given bit representation.
 */
@SinceKotlin("1.2")
@kotlin.internal.InlineOnly
public actual inline fun Float.Companion.fromBits(bits: Int): Float = java.lang.Float.intBitsToFloat(bits)


/**
 * Counts the number of set bits in the binary representation of this [Int] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Int.countOneBits(): Int = Integer.bitCount(this)

/**
 * Counts the number of consecutive most significant bits that are zero in the binary representation of this [Int] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Int.countLeadingZeroBits(): Int = Integer.numberOfLeadingZeros(this)

/**
 * Counts the number of consecutive least significant bits that are zero in the binary representation of this [Int] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Int.countTrailingZeroBits(): Int = Integer.numberOfTrailingZeros(this)

/**
 * Returns a number having a single bit set in the position of the most significant set bit of this [Int] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Int.takeHighestOneBit(): Int = Integer.highestOneBit(this)

/**
 * Returns a number having a single bit set in the position of the least significant set bit of this [Int] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Int.takeLowestOneBit(): Int = Integer.lowestOneBit(this)

/**
 * Rotates the binary representation of this [Int] number left by the specified [bitCount] number of bits.
 * The most significant bits pushed out from the left side reenter the number as the least significant bits on the right side.
 *
 * Rotating the number left by a negative bit count is the same as rotating it right by the negated bit count:
 * `number.rotateLeft(-n) == number.rotateRight(n)`
 *
 * Rotating by a multiple of [Int.SIZE_BITS] (32) returns the same number, or more generally
 * `number.rotateLeft(n) == number.rotateLeft(n % 32)`
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Int.rotateLeft(bitCount: Int): Int = Integer.rotateLeft(this, bitCount)


/**
 * Rotates the binary representation of this [Int] number right by the specified [bitCount] number of bits.
 * The least significant bits pushed out from the right side reenter the number as the most significant bits on the left side.
 *
 * Rotating the number right by a negative bit count is the same as rotating it left by the negated bit count:
 * `number.rotateRight(-n) == number.rotateLeft(n)`
 *
 * Rotating by a multiple of [Int.SIZE_BITS] (32) returns the same number, or more generally
 * `number.rotateRight(n) == number.rotateRight(n % 32)`
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Int.rotateRight(bitCount: Int): Int = Integer.rotateRight(this, bitCount)


/**
 * Counts the number of set bits in the binary representation of this [Long] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Long.countOneBits(): Int = java.lang.Long.bitCount(this)

/**
 * Counts the number of consecutive most significant bits that are zero in the binary representation of this [Long] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Long.countLeadingZeroBits(): Int = java.lang.Long.numberOfLeadingZeros(this)

/**
 * Counts the number of consecutive least significant bits that are zero in the binary representation of this [Long] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Long.countTrailingZeroBits(): Int = java.lang.Long.numberOfTrailingZeros(this)

/**
 * Returns a number having a single bit set in the position of the most significant set bit of this [Long] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Long.takeHighestOneBit(): Long = java.lang.Long.highestOneBit(this)

/**
 * Returns a number having a single bit set in the position of the least significant set bit of this [Long] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Long.takeLowestOneBit(): Long = java.lang.Long.lowestOneBit(this)

/**
 * Rotates the binary representation of this [Long] number left by the specified [bitCount] number of bits.
 * The most significant bits pushed out from the left side reenter the number as the least significant bits on the right side.
 *
 * Rotating the number left by a negative bit count is the same as rotating it right by the negated bit count:
 * `number.rotateLeft(-n) == number.rotateRight(n)`
 *
 * Rotating by a multiple of [Long.SIZE_BITS] (64) returns the same number, or more generally
 * `number.rotateLeft(n) == number.rotateLeft(n % 64)`
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Long.rotateLeft(bitCount: Int): Long = java.lang.Long.rotateLeft(this, bitCount)

/**
 * Rotates the binary representation of this [Long] number right by the specified [bitCount] number of bits.
 * The least significant bits pushed out from the right side reenter the number as the most significant bits on the left side.
 *
 * Rotating the number right by a negative bit count is the same as rotating it left by the negated bit count:
 * `number.rotateRight(-n) == number.rotateLeft(n)`
 *
 * Rotating by a multiple of [Long.SIZE_BITS] (64) returns the same number, or more generally
 * `number.rotateRight(n) == number.rotateRight(n % 64)`
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Long.rotateRight(bitCount: Int): Long = java.lang.Long.rotateRight(this, bitCount)

/**
 * Counts the number of set bits in the binary representation of this [Byte] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Byte.countOneBits(): Int = (toInt() and 0xFF).countOneBits()

/**
 * Counts the number of consecutive most significant bits that are zero in the binary representation of this [Byte] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Byte.countLeadingZeroBits(): Int = (toInt() and 0xFF).countLeadingZeroBits() - (Int.SIZE_BITS - Byte.SIZE_BITS)

/**
 * Counts the number of consecutive least significant bits that are zero in the binary representation of this [Byte] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Byte.countTrailingZeroBits(): Int = (toInt() or 0x100).countTrailingZeroBits()

/**
 * Returns a number having a single bit set in the position of the most significant set bit of this [Byte] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Byte.takeHighestOneBit(): Byte = (toInt() and 0xFF).takeHighestOneBit().toByte()

/**
 * Returns a number having a single bit set in the position of the least significant set bit of this [Byte] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Byte.takeLowestOneBit(): Byte = toInt().takeLowestOneBit().toByte()


/**
 * Rotates the binary representation of this [Byte] number left by the specified [bitCount] number of bits.
 * The most significant bits pushed out from the left side reenter the number as the least significant bits on the right side.
 *
 * Rotating the number left by a negative bit count is the same as rotating it right by the negated bit count:
 * `number.rotateLeft(-n) == number.rotateRight(n)`
 *
 * Rotating by a multiple of [Byte.SIZE_BITS] (8) returns the same number, or more generally
 * `number.rotateLeft(n) == number.rotateLeft(n % 8)`
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
public fun Byte.rotateLeft(bitCount: Int): Byte {
    return (toInt().shl(bitCount and 7) or (toInt() and 0xFF).ushr(8 - (bitCount and 7))).toByte()
}

/**
 * Rotates the binary representation of this [Byte] number right by the specified [bitCount] number of bits.
 * The least significant bits pushed out from the right side reenter the number as the most significant bits on the left side.
 *
 * Rotating the number right by a negative bit count is the same as rotating it left by the negated bit count:
 * `number.rotateRight(-n) == number.rotateLeft(n)`
 *
 * Rotating by a multiple of [Byte.SIZE_BITS] (8) returns the same number, or more generally
 * `number.rotateRight(n) == number.rotateRight(n % 8)`
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
public fun Byte.rotateRight(bitCount: Int): Byte =
    (toInt().shl(8 - (bitCount and 7)) or (toInt() and 0xFF).ushr(bitCount and 7)).toByte()

/**
 * Counts the number of set bits in the binary representation of this [Short] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Short.countOneBits(): Int = (toInt() and 0xFFFF).countOneBits()

/**
 * Counts the number of consecutive most significant bits that are zero in the binary representation of this [Short] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Short.countLeadingZeroBits(): Int = (toInt() and 0xFFFF).countLeadingZeroBits() - (Int.SIZE_BITS - Short.SIZE_BITS)

/**
 * Counts the number of consecutive least significant bits that are zero in the binary representation of this [Short] number.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Short.countTrailingZeroBits(): Int = (toInt() or 0x10000).countTrailingZeroBits()

/**
 * Returns a number having a single bit set in the position of the most significant set bit of this [Short] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Short.takeHighestOneBit(): Short = (toInt() and 0xFFFF).takeHighestOneBit().toShort()

/**
 * Returns a number having a single bit set in the position of the least significant set bit of this [Short] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
@kotlin.internal.InlineOnly
public inline fun Short.takeLowestOneBit(): Short = toInt().takeLowestOneBit().toShort()


/**
 * Rotates the binary representation of this [Short] number left by the specified [bitCount] number of bits.
 * The most significant bits pushed out from the left side reenter the number as the least significant bits on the right side.
 *
 * Rotating the number left by a negative bit count is the same as rotating it right by the negated bit count:
 * `number.rotateLeft(-n) == number.rotateRight(n)`
 *
 * Rotating by a multiple of [Short.SIZE_BITS] (16) returns the same number, or more generally
 * `number.rotateLeft(n) == number.rotateLeft(n % 16)`
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
public fun Short.rotateLeft(bitCount: Int): Short {
    return (toInt().shl(bitCount and 15) or (toInt() and 0xFFFF).ushr(16 - (bitCount and 15))).toShort()
}

/**
 * Rotates the binary representation of this [Short] number right by the specified [bitCount] number of bits.
 * The least significant bits pushed out from the right side reenter the number as the most significant bits on the left side.
 *
 * Rotating the number right by a negative bit count is the same as rotating it left by the negated bit count:
 * `number.rotateRight(-n) == number.rotateLeft(n)`
 *
 * Rotating by a multiple of [Short.SIZE_BITS] (16) returns the same number, or more generally
 * `number.rotateRight(n) == number.rotateRight(n % 16)`
 */
@SinceKotlin("1.3")
@ExperimentalStdlibApi
public fun Short.rotateRight(bitCount: Int): Short =
    (toInt().shl(16 - (bitCount and 15)) or (toInt() and 0xFFFF).ushr(bitCount and 15)).toShort()

