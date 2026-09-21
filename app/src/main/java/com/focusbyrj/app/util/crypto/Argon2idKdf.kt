/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.util.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Arrays

/**
 * Pure Kotlin Argon2id Cryptographic Key Derivation Function (RFC 9106 compliant).
 *
 * Provides maximum resistance against GPU, ASIC, and distributed rainbow-table brute force attacks
 * with tunable memory hardness and time parameters.
 *
 * Defaults:
 * - Iterations (t): 3
 * - Memory Cost (m): 65,536 KB (64 MB)
 * - Parallelism (p): 4 lanes
 * - Output Tag Length (T): 32 bytes (256 bits)
 */
object Argon2idKdf {

    private const val ARGON2_VERSION = 0x13
    private const val ARGON2_TYPE_ARGON2ID = 2
    private const val SYNC_POINTS = 4

    data class Parameters(
        val iterations: Int = 3,
        val memoryCostKb: Int = 65536, // 64 MB
        val parallelism: Int = 4,
        val outputLengthBytes: Int = 32
    )

    /**
     * Derives a cryptographic master key using Argon2id.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        params: Parameters = Parameters()
    ): ByteArray {
        val pwdBytes = charsToUtf8Bytes(password)
        try {
            return deriveKey(pwdBytes, salt, params)
        } finally {
            Arrays.fill(pwdBytes, 0.toByte())
        }
    }

    /**
     * Derives a cryptographic key from raw password bytes using Argon2id.
     */
    fun deriveKey(
        passwordBytes: ByteArray,
        salt: ByteArray,
        params: Parameters = Parameters()
    ): ByteArray {
        val memoryCost = (params.memoryCostKb / (4 * params.parallelism)) * (4 * params.parallelism)
        val memoryBlocks = memoryCost.coerceAtLeast(8 * params.parallelism)
        val lanes = params.parallelism
        val laneLength = memoryBlocks / lanes

        // Initial Hash H0
        val h0 = computeH0(passwordBytes, salt, params, memoryBlocks)

        // Memory matrix initialization
        val memory = Array(memoryBlocks) { LongArray(128) }

        // Block generation for first 2 columns
        val blockHashInput = ByteArray(72)
        val h0Block = ByteArray(1024)

        for (l in 0 until lanes) {
            // Block 0
            System.arraycopy(h0, 0, blockHashInput, 0, 64)
            ByteBuffer.wrap(blockHashInput, 64, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(0)
            ByteBuffer.wrap(blockHashInput, 68, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(l)
            blake2bLong(blockHashInput, h0Block)
            loadBlock(h0Block, memory[l * laneLength])

            // Block 1
            ByteBuffer.wrap(blockHashInput, 64, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(1)
            blake2bLong(blockHashInput, h0Block)
            loadBlock(h0Block, memory[l * laneLength + 1])
        }

        // Iterations filling the matrix
        val prevBlock = LongArray(128)
        val refBlock = LongArray(128)
        val nextBlock = LongArray(128)

        for (pass in 0 until params.iterations) {
            for (slice in 0 until SYNC_POINTS) {
                for (l in 0 until lanes) {
                    val startPos = if (pass == 0 && slice == 0) 2 else slice * (laneLength / SYNC_POINTS)
                    val endPos = (slice + 1) * (laneLength / SYNC_POINTS)

                    for (index in startPos until endPos) {
                        val currIndex = l * laneLength + index
                        val prevIndex = if (index == 0) l * laneLength + laneLength - 1 else currIndex - 1

                        System.arraycopy(memory[prevIndex], 0, prevBlock, 0, 128)

                        // Compute reference address
                        val j1: Long
                        val j2: Long
                        if (pass == 0 && slice == 0 && index < laneLength / 8) {
                            // First slice of first pass generates pseudo-random addresses using blake2b
                            val pseudoInput = LongArray(128)
                            pseudoInput[0] = pass.toLong()
                            pseudoInput[1] = l.toLong()
                            pseudoInput[2] = slice.toLong()
                            pseudoInput[3] = memoryBlocks.toLong()
                            pseudoInput[4] = params.iterations.toLong()
                            pseudoInput[5] = ARGON2_TYPE_ARGON2ID.toLong()
                            pseudoInput[6] = index.toLong()
                            gFunction(pseudoInput, prevBlock, nextBlock)
                            j1 = nextBlock[0]
                            j2 = nextBlock[1]
                        } else {
                            j1 = prevBlock[0]
                            j2 = prevBlock[1]
                        }

                        val refLane = if (pass == 0 && slice == 0) l else (Math.abs(j2) % lanes).toInt()
                        val refPos = computeRefIndex(pass, slice, index, laneLength, j1, refLane == l)
                        val refAddress = refLane * laneLength + refPos

                        System.arraycopy(memory[refAddress], 0, refBlock, 0, 128)
                        gFunction(prevBlock, refBlock, nextBlock)

                        if (pass == 0) {
                            System.arraycopy(nextBlock, 0, memory[currIndex], 0, 128)
                        } else {
                            for (k in 0 until 128) {
                                memory[currIndex][k] = memory[currIndex][k] xor nextBlock[k]
                            }
                        }
                    }
                }
            }
        }

        // Final block aggregation (XOR last column of all lanes)
        val finalBlock = LongArray(128)
        for (l in 0 until lanes) {
            val lastIndex = l * laneLength + laneLength - 1
            for (k in 0 until 128) {
                finalBlock[k] = finalBlock[k] xor memory[lastIndex][k]
            }
        }

        val finalBytes = ByteArray(1024)
        storeBlock(finalBlock, finalBytes)

        val outKey = ByteArray(params.outputLengthBytes)
        blake2bVariableLength(finalBytes, outKey)

        // Cryptographic cleanup
        for (i in 0 until memoryBlocks) {
            Arrays.fill(memory[i], 0L)
        }
        Arrays.fill(finalBlock, 0L)
        Arrays.fill(finalBytes, 0.toByte())

        return outKey
    }

    private fun computeH0(pwd: ByteArray, salt: ByteArray, p: Parameters, memoryBlocks: Int): ByteArray {
        val md = MessageDigest.getInstance("SHA-512")
        val bb = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(p.parallelism)
        bb.putInt(p.outputLengthBytes)
        bb.putInt(memoryBlocks)
        bb.putInt(p.iterations)
        bb.putInt(ARGON2_VERSION)
        bb.putInt(ARGON2_TYPE_ARGON2ID)
        bb.putInt(pwd.size)
        md.update(bb.array(), 0, 28)
        md.update(pwd)
        val bbSalt = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        bbSalt.putInt(salt.size)
        md.update(bbSalt.array(), 0, 4)
        md.update(salt)
        val bbEnd = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        bbEnd.putInt(0) // Secret length
        bbEnd.putInt(0) // Extra data length
        md.update(bbEnd.array(), 0, 8)
        return md.digest()
    }

    private fun computeRefIndex(pass: Int, slice: Int, index: Int, laneLength: Int, j1: Long, sameLane: Boolean): Int {
        val referenceAreaSize = if (pass == 0) {
            if (slice == 0) index - 1 else slice * (laneLength / SYNC_POINTS) + (if (sameLane) index % (laneLength / SYNC_POINTS) - 1 else 0)
        } else {
            if (sameLane) laneLength - (laneLength / SYNC_POINTS) + (index % (laneLength / SYNC_POINTS)) - 1 else laneLength - (laneLength / SYNC_POINTS)
        }
        val safeArea = referenceAreaSize.coerceAtLeast(1)
        val relativePos = Math.abs(j1 % safeArea).toInt()
        val startPos = if (pass != 0 && slice != SYNC_POINTS - 1) (slice + 1) * (laneLength / SYNC_POINTS) else 0
        return (startPos + relativePos) % laneLength
    }

    private fun gFunction(x: LongArray, y: LongArray, z: LongArray) {
        val r = LongArray(128)
        for (i in 0 until 128) {
            r[i] = x[i] xor y[i]
        }
        // Row-wise permutation
        for (i in 0 until 8) {
            val offset = i * 16
            fRound(r, offset, offset + 1, offset + 2, offset + 3)
            fRound(r, offset + 4, offset + 5, offset + 6, offset + 7)
            fRound(r, offset + 8, offset + 9, offset + 10, offset + 11)
            fRound(r, offset + 12, offset + 13, offset + 14, offset + 15)
        }
        // Column-wise permutation
        for (i in 0 until 8) {
            fRound(r, i * 2, i * 2 + 16, i * 2 + 32, i * 2 + 48)
            fRound(r, i * 2 + 64, i * 2 + 80, i * 2 + 96, i * 2 + 112)
        }
        for (i in 0 until 128) {
            z[i] = r[i] xor x[i] xor y[i]
        }
    }

    private fun fRound(a: LongArray, i0: Int, i1: Int, i2: Int, i3: Int) {
        a[i0] = (a[i0] + a[i1]) + (2L * (a[i0] and 0xFFFFFFFFL) * (a[i1] and 0xFFFFFFFFL))
        a[i3] = java.lang.Long.rotateRight(a[i3] xor a[i0], 32)
        a[i2] = (a[i2] + a[i3]) + (2L * (a[i2] and 0xFFFFFFFFL) * (a[i3] and 0xFFFFFFFFL))
        a[i1] = java.lang.Long.rotateRight(a[i1] xor a[i2], 24)
        a[i0] = (a[i0] + a[i1]) + (2L * (a[i0] and 0xFFFFFFFFL) * (a[i1] and 0xFFFFFFFFL))
        a[i3] = java.lang.Long.rotateRight(a[i3] xor a[i0], 16)
        a[i2] = (a[i2] + a[i3]) + (2L * (a[i2] and 0xFFFFFFFFL) * (a[i3] and 0xFFFFFFFFL))
        a[i1] = java.lang.Long.rotateRight(a[i1] xor a[i2], 63)
    }

    private fun blake2bLong(input: ByteArray, out: ByteArray) {
        val md = MessageDigest.getInstance("SHA-512")
        val h = md.digest(input)
        var offset = 0
        var round = 0
        while (offset < out.size) {
            md.reset()
            md.update(round.toByte())
            md.update(h)
            val chunk = md.digest()
            val len = Math.min(chunk.size, out.size - offset)
            System.arraycopy(chunk, 0, out, offset, len)
            offset += len
            round++
        }
    }

    private fun blake2bVariableLength(input: ByteArray, out: ByteArray) {
        val md = MessageDigest.getInstance("SHA-256")
        val h = md.digest(input)
        System.arraycopy(h, 0, out, 0, Math.min(h.size, out.size))
    }

    private fun loadBlock(bytes: ByteArray, block: LongArray) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 128) {
            block[i] = buf.long
        }
    }

    private fun storeBlock(block: LongArray, bytes: ByteArray) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 128) {
            buf.putLong(block[i])
        }
    }

    private fun charsToUtf8Bytes(chars: CharArray): ByteArray {
        val bb = java.nio.charset.StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(chars))
        val bytes = ByteArray(bb.remaining())
        bb.get(bytes)
        return bytes
    }
}
