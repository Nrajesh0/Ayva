package com.focusbyrj.app.util

import kotlin.math.round
import kotlin.math.sqrt
import kotlin.random.Random

enum class ArithmeticDifficulty {
    EASY,    // RRB Clerk Prelims level
    MEDIUM,  // IBPS Clerk / SBI Clerk Prelims level
    HARD     // IBPS PO / RRB PO / SBI PO Prelims level
}

data class ArithmeticQuestion(
    val title: String,
    val questionText: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

object ArithmeticEngine {

    /**
     * Master Generator for Banking Exams:
     * - Easy: RRB Clerk (BODMAS Simplification merging squares, cubes, square roots, cube roots & percentages, basic missing series, direct inequalities)
     * - Medium: SBI/IBPS Clerk (Fraction-percentages, exponents, complex BODMAS, 2-equation quadratics, alternate series)
     * - Hard: IBPS/RRB/SBI PO (Approximations, Wrong Number Series, PO Quadratics with large coefficients / sign tricks, complex patterns)
     */
    fun generateQuestion(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        return when (difficulty) {
            ArithmeticDifficulty.EASY -> {
                when (Random.nextInt(7)) {
                    0 -> generateSpeedMath(difficulty)
                    1 -> generateSimplification(difficulty)
                    2 -> generateNumberSeries(difficulty)
                    3 -> generateDivisibilityAndRemainders(difficulty)
                    4 -> generateFractionBODMAS(difficulty)
                    5 -> generateRatioAndProportion(difficulty)
                    else -> generateInequality(difficulty)
                }
            }
            ArithmeticDifficulty.MEDIUM -> {
                when (Random.nextInt(8)) {
                    0 -> generateSpeedMath(difficulty)
                    1 -> generateSimplification(difficulty)
                    2 -> generateNumberSeries(difficulty)
                    3 -> generateQuadratic(difficulty)
                    4 -> generateDivisibilityAndRemainders(difficulty)
                    5 -> generateFractionBODMAS(difficulty)
                    6 -> generateRatioAndProportion(difficulty)
                    else -> generateInequality(difficulty)
                }
            }
            ArithmeticDifficulty.HARD -> {
                when (Random.nextInt(8)) {
                    0 -> generateApproximation() // PO Core: Approximations
                    1 -> generateSimplification(difficulty)
                    2 -> generateNumberSeries(difficulty) // Includes Wrong Number Series & Hard Patterns
                    3 -> generateQuadratic(difficulty) // Real PO-level quadratics
                    4 -> generateDivisibilityAndRemainders(difficulty)
                    5 -> generateFractionBODMAS(difficulty)
                    6 -> generateRatioAndProportion(difficulty)
                    else -> generateInequality(difficulty)
                }
            }
        }
    }

    // =========================================================================
    // 1. SPEED MATH & POWERS/ROOTS BODMAS (RRB CLERK & PO PRELIMS)
    // =========================================================================
    private val perfectCubes = listOf(
        Pair(8, 2), Pair(27, 3), Pair(64, 4), Pair(125, 5), Pair(216, 6),
        Pair(343, 7), Pair(512, 8), Pair(729, 9), Pair(1000, 10),
        Pair(1331, 11), Pair(1728, 12), Pair(2197, 13), Pair(2744, 14), Pair(3375, 15)
    )

    private fun generateSpeedMath(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        return when (difficulty) {
            ArithmeticDifficulty.EASY -> {
                // RRB Clerk Level: Merged BODMAS rules with Squares, Roots, Cubes & Cube Roots
                when (Random.nextInt(7)) {
                    0 -> {
                        // Square + Multiplication - Square Root
                        val sqNum = Random.nextInt(12, 25)
                        val multA = Random.nextInt(8, 20)
                        val multB = Random.nextInt(4, 12)
                        val rootNum = Random.nextInt(12, 28)
                        val rootSq = rootNum * rootNum
                        val answer = ((sqNum * sqNum) + (multA * multB) - rootNum).toDouble()

                        buildQuestion(
                            title = "Simplification (BODMAS - Powers & Roots)",
                            question = "$sqNum² + $multA × $multB - √$rootSq = ?",
                            answer = answer,
                            distractorLogic = { it + (listOf(-30.0, -15.0, 15.0, 30.0, 50.0).random()) },
                            explanation = "💡 BODMAS Order:\n1. Powers & Roots: $sqNum² = ${sqNum * sqNum}, √$rootSq = $rootNum\n2. Multiplication: $multA × $multB = ${multA * multB}\n3. Addition & Subtraction: ${sqNum * sqNum} + ${multA * multB} - $rootNum = ${formatNumber(answer)}."
                        )
                    }
                    1 -> {
                        // Square Root Division + Square - Cube
                        val divisors = listOf(2, 3, 4, 5, 6, 7)
                        val divisor = divisors.random()
                        val rootQuotient = Random.nextInt(3, 9)
                        val rootNum = divisor * rootQuotient
                        val rootSq = rootNum * rootNum
                        val sqNum = Random.nextInt(11, 22)
                        val cubeBase = Random.nextInt(3, 6)
                        val cubeVal = cubeBase * cubeBase * cubeBase
                        val answer = (rootQuotient + (sqNum * sqNum) - cubeVal).toDouble()

                        buildQuestion(
                            title = "Simplification (BODMAS - Roots & Cubes)",
                            question = "√$rootSq ÷ $divisor + $sqNum² - $cubeBase³ = ?",
                            answer = answer,
                            distractorLogic = { it + (listOf(-35.0, -20.0, 20.0, 35.0, 50.0).random()) },
                            explanation = "💡 BODMAS Order:\n1. Roots & Powers: √$rootSq = $rootNum, $sqNum² = ${sqNum * sqNum}, $cubeBase³ = $cubeVal\n2. Division: $rootNum ÷ $divisor = $rootQuotient\n3. Total: $rootQuotient + ${sqNum * sqNum} - $cubeVal = ${formatNumber(answer)}."
                        )
                    }
                    2 -> {
                        // Bracket BODMAS with Square & Division + Product
                        val sqNum = Random.nextInt(12, 22)
                        val sq = sqNum * sqNum
                        val divisor = listOf(4, 5, 6, 8).random()
                        val bracketQuotient = Random.nextInt(12, 35)
                        val targetInside = divisor * bracketQuotient
                        val subtrahend = sq - targetInside
                        val multA = Random.nextInt(8, 18)
                        val multB = Random.nextInt(3, 8)
                        val answer = (bracketQuotient + (multA * multB)).toDouble()

                        buildQuestion(
                            title = "Simplification (BODMAS - Brackets)",
                            question = "($sqNum² - $subtrahend) ÷ $divisor + $multA × $multB = ?",
                            answer = answer,
                            distractorLogic = { it + (listOf(-40.0, -20.0, 20.0, 30.0, 50.0).random()) },
                            explanation = "💡 BODMAS Order:\n1. Bracket: ($sqNum² - $subtrahend) = ($sq - $subtrahend) = $targetInside\n2. Division: $targetInside ÷ $divisor = $bracketQuotient\n3. Multiplication: $multA × $multB = ${multA * multB}\n4. Total: $bracketQuotient + ${multA * multB} = ${formatNumber(answer)}."
                        )
                    }
                    3 -> {
                        // Cube Root + Square Division + Multiplication (Guaranteed integer division)
                        val (cubeVal, cubeRoot) = perfectCubes.random()
                        val divChoice = listOf(2, 3, 4, 5, 6, 8, 9, 10).random()
                        val sqNum = when (divChoice) {
                            2, 4, 8 -> Random.nextInt(4, 11) * 2
                            3, 6, 9 -> Random.nextInt(2, 6) * 3
                            5, 10 -> Random.nextInt(2, 5) * 5
                            else -> Random.nextInt(2, 5) * divChoice
                        }
                        val sq = sqNum * sqNum
                        val actualDivisors = listOf(2, 3, 4, 5, 6, 8, 9, 10).filter { sq % it == 0 }
                        val div = actualDivisors.random()
                        val sqQuotient = sq / div
                        val multA = Random.nextInt(7, 16)
                        val multB = Random.nextInt(3, 8)
                        val answer = (cubeRoot + sqQuotient + (multA * multB)).toDouble()

                        buildQuestion(
                            title = "Simplification (BODMAS - Cube Roots)",
                            question = "³√$cubeVal + $sqNum² ÷ $div + $multA × $multB = ?",
                            answer = answer,
                            distractorLogic = { it + (listOf(-30.0, -15.0, 15.0, 30.0, 45.0).random()) },
                            explanation = "💡 BODMAS Order:\n1. Cube Root & Power: ³√$cubeVal = $cubeRoot, $sqNum² = $sq\n2. Division: $sq ÷ $div = $sqQuotient\n3. Multiplication: $multA × $multB = ${multA * multB}\n4. Total: $cubeRoot + $sqQuotient + ${multA * multB} = ${formatNumber(answer)}."
                        )
                    }
                    4 -> {
                        // Square Difference Identity Division + Square Root (Guaranteed integer division)
                        val a = Random.nextInt(18, 35)
                        val diff = Random.nextInt(2, 6) * 2 // even difference
                        val b = a - diff
                        val diffOfSq = (a + b) * (a - b)
                        val possibleDivisors = (2..16).filter { diffOfSq % it == 0 }
                        val divisor = if (possibleDivisors.isNotEmpty()) possibleDivisors.random() else 2
                        val rootNum = Random.nextInt(12, 30)
                        val rootSq = rootNum * rootNum
                        val answer = ((diffOfSq / divisor) + rootNum).toDouble()

                        buildQuestion(
                            title = "Simplification (BODMAS - Identities)",
                            question = "($a² - $b²) ÷ $divisor + √$rootSq = ?",
                            answer = answer,
                            distractorLogic = { it + (listOf(-35.0, -20.0, 20.0, 35.0, 50.0).random()) },
                            explanation = "💡 Identity & BODMAS:\n1. ($a² - $b²) = ($a + $b)($a - $b) = ${a + b} × ${a - b} = $diffOfSq\n2. Division: $diffOfSq ÷ $divisor = ${diffOfSq / divisor}\n3. Square Root: √$rootSq = $rootNum\n4. Total: ${diffOfSq / divisor} + $rootNum = ${formatNumber(answer)}."
                        )
                    }
                    5 -> {
                        // Cube Root Multiplication + Square Root - Square Division (Guaranteed integer division)
                        val (cubeVal, cubeRoot) = perfectCubes.random()
                        val cubeMult = Random.nextInt(4, 9)
                        val rootNum = Random.nextInt(12, 30)
                        val rootSq = rootNum * rootNum
                        val divChoice = listOf(2, 3, 4, 5, 6, 8).random()
                        val sqNum = when (divChoice) {
                            2, 4, 8 -> Random.nextInt(3, 8) * 2
                            3, 6 -> Random.nextInt(2, 5) * 3
                            5 -> Random.nextInt(2, 4) * 5
                            else -> Random.nextInt(2, 4) * divChoice
                        }
                        val sq = sqNum * sqNum
                        val actualDivisors = listOf(2, 3, 4, 5, 6, 8).filter { sq % it == 0 }
                        val div = actualDivisors.random()
                        val sqQuotient = sq / div
                        val answer = ((cubeRoot * cubeMult) + rootNum - sqQuotient).toDouble()

                        buildQuestion(
                            title = "Simplification (BODMAS - Mixed Roots)",
                            question = "³√$cubeVal × $cubeMult + √$rootSq - $sqNum² ÷ $div = ?",
                            answer = answer,
                            distractorLogic = { it + (listOf(-25.0, -10.0, 10.0, 25.0, 40.0).random()) },
                            explanation = "💡 BODMAS Order:\n1. Roots & Powers: ³√$cubeVal = $cubeRoot, √$rootSq = $rootNum, $sqNum² = $sq\n2. Multiplication & Division: $cubeRoot × $cubeMult = ${cubeRoot * cubeMult}, $sq ÷ $div = $sqQuotient\n3. Total: ${cubeRoot * cubeMult} + $rootNum - $sqQuotient = ${formatNumber(answer)}."
                        )
                    }
                    else -> {
                        // Percentage + Square - Square Root
                        val pct = listOf(15, 20, 25, 30, 35, 40, 50, 60, 75).random()
                        val base = Random.nextInt(4, 15) * 40
                        val pctVal = (pct * base) / 100
                        val sqNum = Random.nextInt(11, 20)
                        val rootNum = Random.nextInt(12, 28)
                        val rootSq = rootNum * rootNum
                        val answer = (pctVal + (sqNum * sqNum) - rootNum).toDouble()

                        buildQuestion(
                            title = "Simplification (BODMAS - Percentages & Powers)",
                            question = "$pct% of $base + $sqNum² - √$rootSq = ?",
                            answer = answer,
                            distractorLogic = { it + (listOf(-30.0, -15.0, 15.0, 30.0, 45.0).random()) },
                            explanation = "💡 BODMAS Order:\n1. Percentage: $pct% of $base = $pctVal\n2. Power & Root: $sqNum² = ${sqNum * sqNum}, √$rootSq = $rootNum\n3. Total: $pctVal + ${sqNum * sqNum} - $rootNum = ${formatNumber(answer)}."
                        )
                    }
                }
            }
            ArithmeticDifficulty.MEDIUM, ArithmeticDifficulty.HARD -> {
                // SBI/IBPS Clerk & PO: Compound Speed Math identities
                when (Random.nextInt(3)) {
                    0 -> {
                        // a² - b² = (a+b)(a-b)
                        val a = Random.nextInt(25, 65)
                        val diff = Random.nextInt(2, 10) * 2 // ensure even diff for clean math
                        val b = a - diff
                        val answer = ((a + b) * (a - b)).toDouble()
                        buildQuestion(
                            title = "Speed Math (Algebraic Identity)",
                            question = "$a² - $b² = ?",
                            answer = answer,
                            distractorLogic = { it + (listOf(-40.0, -20.0, 20.0, 40.0, 100.0).random()) },
                            explanation = "💡 Use Identity: a² - b² = (a + b)(a - b)\n= ($a + $b) × ($a - $b) = ${a + b} × ${a - b} = ${formatNumber(answer)}."
                        )
                    }
                    1 -> {
                        // Pythagorean or compound roots: √(a² + b²)
                        val triplets = listOf(
                            Triple(15, 20, 25),
                            Triple(12, 16, 20),
                            Triple(7, 24, 25),
                            Triple(9, 12, 15),
                            Triple(20, 21, 29),
                            Triple(12, 35, 37),
                            Triple(16, 30, 34)
                        )
                        val triplet = triplets.random()
                        val mult = Random.nextInt(1, 3)
                        val a = triplet.first * mult
                        val b = triplet.second * mult
                        val ans = (triplet.third * mult).toDouble()
                        buildQuestion(
                            title = "Speed Math (Compound Roots)",
                            question = "√($a² + $b²) = ?",
                            answer = ans,
                            distractorLogic = { it + (listOf(-6.0, -3.0, -1.0, 1.0, 3.0, 5.0).random()) },
                            explanation = "💡 $a² = ${a*a}, $b² = ${b*b}. ${a*a} + ${b*b} = ${ans.toInt()*ans.toInt()}. √${ans.toInt()*ans.toInt()} = ${formatNumber(ans)}."
                        )
                    }
                    else -> {
                        // Square root division + square addition (Guaranteed integer division)
                        val divisor = listOf(2, 3, 4, 5, 6, 7).random()
                        val rootQuotient = Random.nextInt(4, 12)
                        val root = divisor * rootQuotient
                        val sq = root * root
                        val addSq = Random.nextInt(6, 16)
                        val answer = (rootQuotient + (addSq * addSq)).toDouble()
                        buildQuestion(
                            title = "Speed Math (Compound)",
                            question = "√$sq ÷ $divisor + $addSq² = ?",
                            answer = answer,
                            distractorLogic = { it + (listOf(-30.0, -15.0, 10.0, 20.0, 40.0).random()) },
                            explanation = "💡 √$sq = $root. $root ÷ $divisor = $rootQuotient. $addSq² = ${addSq*addSq}. $rootQuotient + ${addSq*addSq} = ${formatNumber(answer)}."
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // 2. SIMPLIFICATION (BANK FRACTIONS, BODMAS, EXPONENTS, CLERK/PO PRELIMS)
    // =========================================================================
    private data class MagicFraction(val display: String, val num: Int, val den: Int, val tip: String)

    private val bankFractions = listOf(
        MagicFraction("14.28%", 1, 7, "1/7"),
        MagicFraction("28.56%", 2, 7, "2/7"),
        MagicFraction("42.85%", 3, 7, "3/7"),
        MagicFraction("57.14%", 4, 7, "4/7"),
        MagicFraction("16.66%", 1, 6, "1/6"),
        MagicFraction("83.33%", 5, 6, "5/6"),
        MagicFraction("33.33%", 1, 3, "1/3"),
        MagicFraction("66.66%", 2, 3, "2/3"),
        MagicFraction("12.5%", 1, 8, "1/8"),
        MagicFraction("37.5%", 3, 8, "3/8"),
        MagicFraction("62.5%", 5, 8, "5/8"),
        MagicFraction("87.5%", 7, 8, "7/8"),
        MagicFraction("11.11%", 1, 9, "1/9"),
        MagicFraction("22.22%", 2, 9, "2/9"),
        MagicFraction("44.44%", 4, 9, "4/9"),
        MagicFraction("9.09%", 1, 11, "1/11"),
        MagicFraction("18.18%", 2, 11, "2/11"),
        MagicFraction("27.27%", 3, 11, "3/11"),
        MagicFraction("7.14%", 1, 14, "1/14"),
        MagicFraction("6.25%", 1, 16, "1/16")
    )

    private fun generateSimplification(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        val pattern = Random.nextInt(7)
        return when (pattern) {
            0 -> {
                // Percentage to Fraction Conversions (Classic Bank Exam Simplification)
                val fraction = bankFractions.random()
                val multiplier = when (difficulty) {
                    ArithmeticDifficulty.EASY -> Random.nextInt(3, 10)
                    ArithmeticDifficulty.MEDIUM -> Random.nextInt(10, 30)
                    ArithmeticDifficulty.HARD -> Random.nextInt(25, 60)
                }
                val base = multiplier * fraction.den * 10
                val part1Answer = (base / fraction.den) * fraction.num
                val addition = Random.nextInt(5, 30) * 10
                val isAddition = Random.nextBoolean()
                val finalAnswer = (if (isAddition) part1Answer + addition else Math.abs(part1Answer - addition)).toDouble()
                val opStr = if (isAddition) "+" else "-"

                buildQuestion(
                    title = "Simplification (Bank Fractions)",
                    question = "${fraction.display} of $base $opStr $addition = ?",
                    answer = finalAnswer,
                    distractorLogic = { it + (listOf(-30.0, -15.0, 15.0, 30.0, 50.0).random()) },
                    explanation = "💡 Tip: ${fraction.display} = ${fraction.tip}.\n(${fraction.num}/${fraction.den}) × $base = $part1Answer.\n$part1Answer $opStr $addition = ${formatNumber(finalAnswer)}."
                )
            }
            1 -> {
                // Double Percentage & Mixed Sums
                val p1 = listOf(15, 25, 35, 45, 65, 75).random()
                val v1 = Random.nextInt(2, 10) * 40
                val p2 = listOf(20, 30, 40, 50, 60, 80).random()
                val v2 = Random.nextInt(2, 8) * 50
                val res1 = (p1 * v1) / 100
                val res2 = (p2 * v2) / 100
                val finalAnswer = (res1 + res2).toDouble()

                buildQuestion(
                    title = "Simplification (Percentages)",
                    question = "$p1% of $v1 + $p2% of $v2 = ?",
                    answer = finalAnswer,
                    distractorLogic = { it + (listOf(-25.0, -10.0, 10.0, 20.0, 35.0).random()) },
                    explanation = "💡 $p1% of $v1 = $res1\n$p2% of $v2 = $res2\nTotal = $res1 + $res2 = ${formatNumber(finalAnswer)}."
                )
            }
            2 -> {
                // Nested BODMAS with brackets and division
                val c = Random.nextInt(3, 9)
                val b = c * Random.nextInt(3, 9)
                val a = Random.nextInt(12, 35)
                val d = Random.nextInt(15, 80)
                val finalAnswer = ((a * (b / c)) + d).toDouble()

                buildQuestion(
                    title = "Simplification (BODMAS)",
                    question = "$a × ($b ÷ $c) + $d = ?",
                    answer = finalAnswer,
                    distractorLogic = { it + (listOf(-40.0, -20.0, 20.0, 30.0, 50.0).random()) },
                    explanation = "💡 BODMAS Order:\n1. Bracket/Division: $b ÷ $c = ${b/c}\n2. Multiplication: $a × ${b/c} = ${a * (b/c)}\n3. Addition: ${a * (b/c)} + $d = ${formatNumber(finalAnswer)}."
                )
            }
            3 -> {
                // Exponents & Powers Comparison (e.g., 4^? × 2^3 = 2^9)
                val base = listOf(2, 3, 5).random()
                val targetPower = Random.nextInt(5, 11)
                val knownPower = Random.nextInt(2, targetPower - 1)
                val missingPower = targetPower - knownPower

                val isSquareBase = (base == 2 && Random.nextBoolean())
                val displayMissing = if (isSquareBase) "(4)^? × $base^$knownPower = $base^$targetPower" else "$base^? × $base^$knownPower = $base^$targetPower"
                val answer = if (isSquareBase) (missingPower / 2.0) else missingPower.toDouble()

                if (isSquareBase && missingPower % 2 != 0) {
                    // Fallback to direct same base
                    val directMissing = (targetPower - knownPower).toDouble()
                    buildQuestion(
                        title = "Simplification (Exponents)",
                        question = "$base^? × $base^$knownPower = $base^$targetPower",
                        answer = directMissing,
                        distractorLogic = { it + (listOf(-3.0, -1.0, 1.0, 2.0, 3.0).random()) },
                        explanation = "💡 Since bases are identical: ? + $knownPower = $targetPower ➔ ? = $targetPower - $knownPower = ${formatNumber(directMissing)}."
                    )
                } else {
                    buildQuestion(
                        title = "Simplification (Exponents)",
                        question = displayMissing,
                        answer = answer,
                        distractorLogic = { it + (listOf(-2.0, -1.0, 1.0, 2.0, 3.0).random()) },
                        explanation = "💡 Convert to common base $base:\nPower equation: ${if (isSquareBase) "2(?)" else "?"} + $knownPower = $targetPower ➔ ? = ${formatNumber(answer)}."
                    )
                }
            }
            4 -> {
                // Mixed Fractions (RRB & IBPS Clerk classic)
                val w1 = Random.nextInt(2, 6)
                val w2 = Random.nextInt(1, 4)
                val den = listOf(4, 6, 8).random()
                val n1 = 1
                val n2 = 3
                val sumWhole = w1 + w2
                val fracSumNum = n1 + n2
                val totalFraction = sumWhole + (fracSumNum.toDouble() / den)

                buildQuestion(
                    title = "Simplification (Mixed Fractions)",
                    question = "$w1 ${n1}/$den + $w2 ${n2}/$den = ?",
                    answer = totalFraction,
                    distractorLogic = { it + (listOf(-1.5, -0.5, 0.5, 1.0, 1.5).random()) },
                    explanation = "💡 Separate whole numbers and fractions:\nWhole: $w1 + $w2 = $sumWhole\nFractions: $n1/$den + $n2/$den = $fracSumNum/$den = ${fracSumNum.toDouble() / den}\nTotal = ${formatNumber(totalFraction)}."
                )
            }
            5 -> {
                // Root product with offset (e.g., √144 × √25 + 40 = ?)
                val roots = listOf(12, 14, 15, 16, 18, 20, 25)
                val r1 = roots.random()
                val r2 = listOf(5, 10, 12, 15).random()
                val offset = Random.nextInt(10, 50)
                val answer = ((r1 * r2) + offset).toDouble()

                buildQuestion(
                    title = "Simplification (Surds)",
                    question = "√${r1 * r1} × √${r2 * r2} + $offset = ?",
                    answer = answer,
                    distractorLogic = { it + (listOf(-30.0, -15.0, 15.0, 30.0, 45.0).random()) },
                    explanation = "💡 √${r1 * r1} = $r1, √${r2 * r2} = $r2.\n($r1 × $r2) + $offset = ${r1 * r2} + $offset = ${formatNumber(answer)}."
                )
            }
            else -> {
                // Fraction Cancellation & Division
                val d1 = Random.nextInt(4, 9)
                val n1 = Random.nextInt(2, d1)
                val mult = Random.nextInt(3, 8)
                val valTotal = d1 * mult * 10
                val answer = ((valTotal / d1) * n1).toDouble()

                buildQuestion(
                    title = "Simplification (Fractions)",
                    question = "($n1/$d1) of $valTotal = ?",
                    answer = answer,
                    distractorLogic = { it + (listOf(-20.0, -10.0, 10.0, 20.0, 40.0).random()) },
                    explanation = "💡 $valTotal ÷ $d1 = ${valTotal / d1}.\nMultiply by $n1: ${valTotal / d1} × $n1 = ${formatNumber(answer)}."
                )
            }
        }
    }

    // =========================================================================
    // 3. APPROXIMATIONS (IBPS PO & SBI PO Prelims Core Module)
    // =========================================================================
    private fun generateApproximation(): ArithmeticQuestion {
        return when (Random.nextInt(3)) {
            0 -> {
                // Percentage approximation: e.g. 49.98% of 749.89 + 24.12% of 400.2 = ?
                val p1 = listOf(20, 25, 40, 50, 75).random()
                val p1Dec = p1 - 0.05 + (Random.nextInt(10) * 0.01)
                val v1 = Random.nextInt(2, 8) * 100
                val v1Dec = v1 - 0.15 + (Random.nextInt(30) * 0.01)

                val p2 = listOf(10, 20, 30).random()
                val p2Dec = p2 + 0.05 + (Random.nextInt(10) * 0.01)
                val v2 = Random.nextInt(2, 6) * 100
                val v2Dec = v2 + 0.12 - (Random.nextInt(20) * 0.01)

                val approx1 = (p1 * v1) / 100
                val approx2 = (p2 * v2) / 100
                val answer = (approx1 + approx2).toDouble()

                buildQuestion(
                    title = "Approximation (PO Level)",
                    question = "${round2(p1Dec)}% of ${round2(v1Dec)} + ${round2(p2Dec)}% of ${round2(v2Dec)} ≈ ?",
                    answer = answer,
                    distractorLogic = { it + (listOf(-35.0, -20.0, 20.0, 35.0, 50.0).random()) },
                    explanation = "💡 Approximate to nearest round numbers:\n${round2(p1Dec)}% ≈ $p1%, ${round2(v1Dec)} ≈ $v1 ➔ $p1% of $v1 = $approx1\n${round2(p2Dec)}% ≈ $p2%, ${round2(v2Dec)} ≈ $v2 ➔ $p2% of $v2 = $approx2\nSum ≈ $approx1 + $approx2 = ${formatNumber(answer)}."
                )
            }
            1 -> {
                // Root approximation: e.g. √1443.98 ÷ 18.91 + 14.89² ≈ ?
                val rootBase = listOf(25, 30, 35, 40).random()
                val sqNear = (rootBase * rootBase) + (Random.nextInt(-3, 4)) + 0.88
                val divBase = listOf(5, 6, 7).filter { rootBase % it == 0 }.randomOrNull() ?: 5
                val divNear = divBase + 0.08 - (Random.nextInt(15) * 0.01)
                val sqBase = Random.nextInt(10, 16)
                val sqNearVal = sqBase + 0.12

                val part1 = rootBase / divBase
                val part2 = sqBase * sqBase
                val answer = (part1 + part2).toDouble()

                buildQuestion(
                    title = "Approximation (PO Level)",
                    question = "√${round2(sqNear)} ÷ ${round2(divNear)} + (${round2(sqNearVal)})² ≈ ?",
                    answer = answer,
                    distractorLogic = { it + (listOf(-40.0, -20.0, 20.0, 40.0, 60.0).random()) },
                    explanation = "💡 Approximate each term:\n√${round2(sqNear)} ≈ $rootBase\n${round2(divNear)} ≈ $divBase ➔ $rootBase ÷ $divBase = $part1\n(${round2(sqNearVal)})² ≈ $sqBase² = $part2\nTotal ≈ $part1 + $part2 = ${formatNumber(answer)}."
                )
            }
            else -> {
                // Product and division approximation
                val a = Random.nextInt(15, 35)
                val aDec = a + 0.11
                val b = Random.nextInt(8, 20)
                val bDec = b - 0.09
                val c = Random.nextInt(20, 80)
                val cDec = c + 0.22
                val answer = ((a * b) + c).toDouble()

                buildQuestion(
                    title = "Approximation (PO Level)",
                    question = "${round2(aDec)} × ${round2(bDec)} + ${round2(cDec)} ≈ ?",
                    answer = answer,
                    distractorLogic = { it + (listOf(-30.0, -15.0, 15.0, 30.0, 50.0).random()) },
                    explanation = "💡 Approximate:\n${round2(aDec)} ≈ $a, ${round2(bDec)} ≈ $b ➔ $a × $b = ${a*b}\n${round2(cDec)} ≈ $c ➔ ${a*b} + $c = ${formatNumber(answer)}."
                )
            }
        }
    }

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0

    // =========================================================================
    // 4. NUMBER SERIES (MISSING & WRONG NUMBER SERIES)
    // =========================================================================
    private fun generateNumberSeries(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        // In PO exams (HARD), randomly assign Wrong Number Series!
        if (difficulty == ArithmeticDifficulty.HARD && Random.nextBoolean()) {
            return generateWrongNumberSeries()
        }

        val length = 5
        val sequence = mutableListOf<Double>()
        var start = Random.nextInt(4, 18).toDouble()
        sequence.add(start)

        var explanationStr = ""
        val patternChoice = Random.nextInt(7)

        when (difficulty) {
            ArithmeticDifficulty.EASY -> {
                // RRB Clerk: Single difference, squares, cubes, simple additions
                when (patternChoice) {
                    0 -> {
                        val diffStart = Random.nextInt(2, 7)
                        val diffs = (diffStart until diffStart + length).map { (it * it).toDouble() }
                        for (i in 0 until length - 1) sequence.add(sequence.last() + diffs[i])
                        explanationStr = "Difference is consecutive squares (+${formatNumber(diffs[0])}, +${formatNumber(diffs[1])}, +${formatNumber(diffs[2])})."
                    }
                    1 -> {
                        val mult = Random.nextInt(3, 8).toDouble()
                        for (i in 1 until length) sequence.add(sequence.last() + (mult * i))
                        explanationStr = "Difference is multiples of ${formatNumber(mult)} (+${formatNumber(mult)}, +${formatNumber(mult*2)}...)."
                    }
                    2 -> {
                        val diff = Random.nextInt(14, 38).toDouble()
                        for (i in 1 until length) sequence.add(sequence.last() + diff)
                        explanationStr = "Constant difference of +${formatNumber(diff)}."
                    }
                    3 -> {
                        val isCube = Random.nextBoolean()
                        val startNum = Random.nextInt(3, 10)
                        sequence.clear()
                        for (i in 0 until length) {
                            val n = (startNum + i).toDouble()
                            sequence.add(if (isCube) n * n * n else n * n)
                        }
                        explanationStr = "Terms are consecutive ${if (isCube) "cubes" else "squares"} of $startNum, ${startNum + 1}..."
                    }
                    4 -> {
                        val startAdd = Random.nextInt(3, 11).toDouble()
                        for (i in 0 until length - 1) sequence.add(sequence.last() + startAdd + (i * 2))
                        explanationStr = "Difference increases by +2 each step (+${formatNumber(startAdd)}, +${formatNumber(startAdd + 2)}...)."
                    }
                    5 -> {
                        val primes = listOf(2.0, 3.0, 5.0, 7.0, 11.0, 13.0, 17.0, 19.0, 23.0)
                        val pStart = Random.nextInt(0, primes.size - length)
                        for (i in 0 until length - 1) sequence.add(sequence.last() + primes[pStart + i])
                        explanationStr = "Difference is consecutive prime numbers (+${formatNumber(primes[pStart])}, +${formatNumber(primes[pStart+1])})."
                    }
                    else -> {
                        val mult = Random.nextInt(2, 4).toDouble()
                        sequence.clear()
                        sequence.add(Random.nextInt(3, 8).toDouble())
                        for (i in 1 until length) sequence.add(sequence.last() * mult)
                        explanationStr = "Each term is multiplied by ${formatNumber(mult)}."
                    }
                }
            }
            ArithmeticDifficulty.MEDIUM -> {
                // IBPS/SBI Clerk: Alternate series, (×n + c), double differences
                when (patternChoice) {
                    0 -> {
                        val mult = Random.nextInt(2, 4).toDouble()
                        for (i in 1 until length) sequence.add(sequence.last() * mult + i)
                        explanationStr = "Pattern: (Previous × ${formatNumber(mult)}) + n where n is 1, 2, 3..."
                    }
                    1 -> {
                        val addVal = Random.nextInt(15, 35).toDouble()
                        val subVal = Random.nextInt(5, 15).toDouble()
                        for (i in 1 until length) {
                            if (i % 2 != 0) sequence.add(sequence.last() + addVal)
                            else sequence.add(sequence.last() - subVal)
                        }
                        explanationStr = "Alternating differences (+${formatNumber(addVal)}, -${formatNumber(subVal)})."
                    }
                    2 -> {
                        var diff = Random.nextInt(3, 8).toDouble()
                        val mult = 2.0
                        for (i in 1 until length) {
                            sequence.add(sequence.last() + diff)
                            diff *= mult
                        }
                        explanationStr = "The difference doubles each step."
                    }
                    3 -> {
                        var diff = Random.nextInt(4, 12).toDouble()
                        val doubleDiff = Random.nextInt(3, 7).toDouble()
                        for (i in 1 until length) {
                            sequence.add(sequence.last() + diff)
                            diff += doubleDiff
                        }
                        explanationStr = "Double difference is constant (+${formatNumber(doubleDiff)})."
                    }
                    4 -> {
                        for (i in 1 until length) sequence.add((sequence.last() * i) + i)
                        explanationStr = "Pattern: (Previous × 1)+1, (× 2)+2, (× 3)+3..."
                    }
                    5 -> {
                        val sign = if (Random.nextBoolean()) 1 else -1
                        val diffStart = Random.nextInt(2, 6)
                        for (i in 0 until length - 1) {
                            val n = (diffStart + i).toDouble()
                            sequence.add(sequence.last() + (n * n + sign))
                        }
                        explanationStr = "Difference is (n² ${if (sign > 0) "+" else "-"} 1)."
                    }
                    else -> {
                        sequence.clear()
                        sequence.add(Random.nextInt(2, 8).toDouble())
                        sequence.add(Random.nextInt(8, 16).toDouble())
                        for (i in 2 until length) sequence.add(sequence[i - 1] + sequence[i - 2])
                        explanationStr = "Fibonacci-style: Next term is the sum of previous two terms."
                    }
                }
            }
            ArithmeticDifficulty.HARD -> {
                // IBPS/RRB PO: Decimal multipliers (×0.5 + 0.5), (n³ ± n²), alternating operations
                when (patternChoice) {
                    0 -> {
                        // Standard Bank PO Series: ×0.5 + 1, ×1 + 1, ×1.5 + 1...
                        start = listOf(16.0, 24.0, 32.0, 48.0, 64.0).random()
                        sequence.clear()
                        sequence.add(start)
                        var current = start
                        var mult = 0.5
                        for (i in 1 until length) {
                            current = round((current * mult + 1.0) * 100) / 100.0
                            sequence.add(current)
                            mult += 0.5
                        }
                        explanationStr = "Pattern: (Previous × 0.5)+1, (× 1)+1, (× 1.5)+1, (× 2)+1..."
                    }
                    1 -> {
                        // ×0.5 + 0.5, ×1 + 1, ×1.5 + 1.5...
                        start = listOf(14.0, 20.0, 28.0, 36.0).random()
                        sequence.clear()
                        sequence.add(start)
                        var current = start
                        var mult = 0.5
                        for (i in 1 until length) {
                            current = round((current * mult + mult) * 100) / 100.0
                            sequence.add(current)
                            mult += 0.5
                        }
                        explanationStr = "Pattern: (Previous × 0.5)+0.5, (× 1)+1, (× 1.5)+1.5..."
                    }
                    2 -> {
                        val mult = Random.nextInt(2, 4).toDouble()
                        for (i in 1 until length) sequence.add(sequence.last() * mult - i)
                        explanationStr = "Pattern: (Previous × ${formatNumber(mult)}) - n where n is 1, 2, 3..."
                    }
                    3 -> {
                        val startN = Random.nextInt(1, 4).toDouble()
                        for (i in 0 until length - 1) {
                            val n = startN + i
                            sequence.add(sequence.last() + (n * n * n) + (n * n))
                        }
                        explanationStr = "Difference is (n³ + n²)."
                    }
                    4 -> {
                        val sign = if (Random.nextBoolean()) 1 else -1
                        val diffStart = Random.nextInt(1, 4).toDouble()
                        for (i in 0 until length - 1) {
                            val n = diffStart + i
                            sequence.add(sequence.last() + (n * n * n + sign))
                        }
                        explanationStr = "Difference is (n³ ${if (sign > 0) "+" else "-"} 1)."
                    }
                    5 -> {
                        var diff = Random.nextInt(3, 8).toDouble()
                        val squareStart = Random.nextInt(2, 5).toDouble()
                        for (i in 0 until length - 1) {
                            sequence.add(sequence.last() + diff)
                            val n = squareStart + i
                            diff += (n * n)
                        }
                        explanationStr = "Double difference is consecutive squares."
                    }
                    else -> {
                        // (×1 - 1), (×2 + 2), (×3 - 3)...
                        var cur = Random.nextInt(3, 8).toDouble()
                        sequence.clear()
                        sequence.add(cur)
                        for (i in 1 until length) {
                            val op = if (i % 2 != 0) -i else i
                            cur = (cur * i) + op
                            sequence.add(cur)
                        }
                        explanationStr = "Pattern: (×1 - 1), (×2 + 2), (×3 - 3), (×4 + 4)..."
                    }
                }
            }
        }

        val answer = sequence.last()
        val questionStr = sequence.dropLast(1).joinToString(", ") { formatNumber(it) } + ", ?"

        return buildQuestion(
            title = "Missing Number Series",
            question = questionStr,
            answer = answer,
            distractorLogic = { it + (listOf(-8.0, -4.0, 4.0, 8.0, 15.0).random()) },
            explanation = "💡 $explanationStr\nMissing term: ${formatNumber(answer)}."
        )
    }

    // Wrong Number Series (Specific to PO Prelims)
    private fun generateWrongNumberSeries(): ArithmeticQuestion {
        val length = 6
        val correctSeq = mutableListOf<Double>()
        var patternExplanation = ""
        val patternType = Random.nextInt(3)

        when (patternType) {
            0 -> {
                // Pattern 1: Differences of consecutive squares (+n²)
                val start = Random.nextInt(4, 20).toDouble()
                correctSeq.add(start)
                val diffStart = Random.nextInt(2, 6)
                val diffs = (diffStart until diffStart + length).map { (it * it).toDouble() }
                for (i in 0 until length - 1) {
                    correctSeq.add(correctSeq.last() + diffs[i])
                }
                patternExplanation = "Differences are consecutive squares (+${formatNumber(diffs[0])}, +${formatNumber(diffs[1])}, +${formatNumber(diffs[2])}...)."
            }
            1 -> {
                // Pattern 2: Differences of consecutive cubes (+n³)
                val start = Random.nextInt(5, 30).toDouble()
                correctSeq.add(start)
                val cubeStart = Random.nextInt(2, 5)
                val diffs = (cubeStart until cubeStart + length).map { (it * it * it).toDouble() }
                for (i in 0 until length - 1) {
                    correctSeq.add(correctSeq.last() + diffs[i])
                }
                patternExplanation = "Differences are consecutive cubes (+${formatNumber(diffs[0])}, +${formatNumber(diffs[1])}, +${formatNumber(diffs[2])}...)."
            }
            else -> {
                // Pattern 3: Classic Bank Decimal Multiplier Pattern (×0.5 + 1, ×1 + 1, ×1.5 + 1, ×2 + 1, ×2.5 + 1)
                var current = listOf(8.0, 12.0, 16.0, 20.0, 24.0, 32.0).random()
                correctSeq.add(current)
                var mult = 0.5
                for (i in 0 until length - 1) {
                    current = (current * mult) + 1.0
                    correctSeq.add(current)
                    mult += 0.5
                }
                patternExplanation = "Pattern follows: (Term × 0.5 + 1), (Term × 1.0 + 1), (Term × 1.5 + 1), (Term × 2.0 + 1)..."
            }
        }

        // Pick one middle term to corrupt
        val wrongIdx = Random.nextInt(1, length - 1)
        val actualCorrect = correctSeq[wrongIdx]
        val wrongOffset = listOf(-6.0, -4.0, -2.0, 2.0, 4.0, 6.0).random()
        val wrongValue = actualCorrect + wrongOffset

        val displaySeq = correctSeq.toMutableList()
        displaySeq[wrongIdx] = wrongValue

        val questionStr = displaySeq.joinToString(", ") { formatNumber(it) }

        // The 5 options in bank exams are 5 terms from the given series, ALWAYS including the wrong number
        val otherTerms = displaySeq.filterIndexed { index, _ -> index != wrongIdx }.shuffled().take(4)
        val candidateOptions = (otherTerms + wrongValue).shuffled()

        val explanation = """
            💡 **Wrong Number Series Pattern:**
            $patternExplanation
            At position ${wrongIdx + 1}, the value is ${formatNumber(wrongValue)}, but it should be ${formatNumber(actualCorrect)}.
            Therefore, **${formatNumber(wrongValue)}** is the wrong number in the series.
        """.trimIndent()

        val correctIndex = candidateOptions.indexOf(wrongValue).takeIf { it != -1 } ?: 0

        return ArithmeticQuestion(
            title = "Wrong Number Series (PO Level)",
            questionText = "Find the wrong number in the series:\n$questionStr",
            options = candidateOptions.map { formatNumber(it) },
            correctIndex = correctIndex,
            explanation = explanation
        )
    }

    // =========================================================================
    // 5. QUADRATIC EQUATIONS (BANK CLERK & PO PRELIMS)
    // =========================================================================
    private fun generateQuadratic(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        // In PO difficulty, 25% of the time test the high-speed 10-Second Sign Trick:
        // When constant terms c1 < 0 and c2 < 0, both equations have one positive and one negative root.
        // Therefore, relationship is ALWAYS "Relationship cannot be established" (CND) without calculation!
        val isSignTrick = (difficulty == ArithmeticDifficulty.HARD && Random.nextInt(4) == 0)

        val options = listOf(
            "x > y",
            "x < y",
            "x ≥ y",
            "x ≤ y",
            "x = y or Relationship cannot be established"
        )

        if (isSignTrick) {
            val aX = listOf(2, 3, 5).random()
            val aY = listOf(2, 4, 6).random()
            val rXPos = Random.nextInt(3, 9)
            val rXNeg = -Random.nextInt(2, 7)
            val rYPos = Random.nextInt(2, 8)
            val rYNeg = -Random.nextInt(3, 9)

            val eqX = formatQuadratic("x", aX, -aX * (rXPos + rXNeg), aX * (rXPos * rXNeg))
            val eqY = formatQuadratic("y", aY, -aY * (rYPos + rYNeg), aY * (rYPos * rYNeg))

            val explanation = """
                ⚡ **10-Second Bank Exam Sign Shortcut:**
                Notice the constant terms in both equations:
                • Equation I constant term is negative ($eqX)
                • Equation II constant term is negative ($eqY)
                
                **Rule:** When both constant terms are negative, each equation produces one positive root and one negative root (+/- and +/-).
                When cross-comparing, the positive root of x is greater than negative root of y, but negative root of x is smaller than positive root of y.
                Hence, **Relationship cannot be established (CND)** without calculating any roots!
            """.trimIndent()

            return ArithmeticQuestion(
                title = "Quadratic Equations (Speed Sign Trick)",
                questionText = "I. $eqX\nII. $eqY",
                options = options,
                correctIndex = 4,
                explanation = explanation
            )
        }

        val relType = Random.nextInt(5)
        // 0: x > y, 1: x < y, 2: x >= y, 3: x <= y, 4: Relationship cannot be established

        var rx1 = 0
        var rx2 = 0
        var ry1 = 0
        var ry2 = 0

        when (relType) {
            0 -> { // x > y
                rx1 = Random.nextInt(3, 10)
                rx2 = Random.nextInt(rx1, 14)
                ry1 = Random.nextInt(-4, rx1 - 1)
                ry2 = Random.nextInt(ry1, rx1 - 1)
            }
            1 -> { // x < y
                ry1 = Random.nextInt(3, 10)
                ry2 = Random.nextInt(ry1, 14)
                rx1 = Random.nextInt(-4, ry1 - 1)
                rx2 = Random.nextInt(rx1, ry1 - 1)
            }
            2 -> { // x >= y
                rx1 = Random.nextInt(3, 10)
                ry1 = rx1
                ry2 = Random.nextInt(-4, ry1 - 1)
                rx2 = Random.nextInt(rx1 + 1, 14)
            }
            3 -> { // x <= y
                ry1 = Random.nextInt(3, 10)
                rx1 = ry1
                rx2 = Random.nextInt(-4, rx1 - 1)
                ry2 = Random.nextInt(ry1 + 1, 14)
            }
            4 -> { // No relationship / overlapping roots
                rx1 = Random.nextInt(2, 8)
                ry1 = rx1 - 2
                rx2 = Random.nextInt(ry1 + 1, 11)
                ry2 = rx2 + 2
            }
        }

        val xRoots = listOf(rx1, rx2).shuffled()
        val yRoots = listOf(ry1, ry2).shuffled()

        // In PO level, use actual coefficients (aX != 1 or aY != 1) such as 2, 3, 4, 5
        val aX = when (difficulty) {
            ArithmeticDifficulty.HARD -> listOf(2, 3, 4, 5, 6).random()
            ArithmeticDifficulty.MEDIUM -> if (Random.nextBoolean()) 2 else 1
            else -> 1
        }
        val aY = when (difficulty) {
            ArithmeticDifficulty.HARD -> listOf(2, 3, 4, 5, 6).random()
            ArithmeticDifficulty.MEDIUM -> if (Random.nextBoolean()) 2 else 1
            else -> 1
        }

        val eqX = formatQuadratic("x", aX, -aX * (xRoots[0] + xRoots[1]), aX * (xRoots[0] * xRoots[1]))
        val eqY = formatQuadratic("y", aY, -aY * (yRoots[0] + yRoots[1]), aY * (yRoots[0] * yRoots[1]))

        val explanation = """
            💡 **Step-by-step Factoring:**
            Equation I: $eqX
            Roots of x: ${xRoots[0]}, ${xRoots[1]}
            
            Equation II: $eqY
            Roots of y: ${yRoots[0]}, ${yRoots[1]}
            
            **Comparison Matrix:**
            • (${xRoots[0]} vs ${yRoots[0]}) ➔ ${cmp(xRoots[0], yRoots[0])}
            • (${xRoots[0]} vs ${yRoots[1]}) ➔ ${cmp(xRoots[0], yRoots[1])}
            • (${xRoots[1]} vs ${yRoots[0]}) ➔ ${cmp(xRoots[1], yRoots[0])}
            • (${xRoots[1]} vs ${yRoots[1]}) ➔ ${cmp(xRoots[1], yRoots[1])}
            
            **Final Answer:** ${options[relType]}
        """.trimIndent()

        return ArithmeticQuestion(
            title = "Quadratic Equations",
            questionText = "I. $eqX\nII. $eqY",
            options = options,
            correctIndex = relType,
            explanation = explanation
        )
    }

    private fun cmp(x: Int, y: Int): String = when {
        x > y -> "x > y"
        x < y -> "x < y"
        else -> "x = y"
    }

    private fun formatQuadratic(v: String, a: Int, b: Int, c: Int): String {
        val aStr = if (a == 1) "$v²" else if (a == -1) "-$v²" else "$a$v²"
        val bStr = if (b == 1) "+ $v" else if (b == -1) "- $v" else if (b > 0) "+ $b$v" else if (b < 0) "- ${-b}$v" else ""
        val cStr = if (c > 0) "+ $c" else if (c < 0) "- ${-c}" else ""
        return "$aStr $bStr $cStr = 0".replace(Regex(" +"), " ").trim()
    }

    // =========================================================================
    // HELPER: FORMAT & BUILD 5 OPTIONS SAFELY WITH HUMAN-LIKE DISTRACTORS
    // =========================================================================
    fun formatNumber(num: Double): String {
        // Prevent IEEE-754 precision drift (e.g. 24.000000000000004 -> 24)
        val rounded = round(num * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    private fun buildQuestion(
        title: String,
        question: String,
        answer: Double,
        distractorLogic: (Double) -> Double,
        explanation: String
    ): ArithmeticQuestion {
        val optionsSet = mutableSetOf<Double>()
        val cleanAnswer = round(answer * 100.0) / 100.0
        optionsSet.add(cleanAnswer)

        val allowNegative = cleanAnswer < 0
        var attempts = 0
        while (optionsSet.size < 5 && attempts < 30) {
            val dist = round(distractorLogic(cleanAnswer) * 100.0) / 100.0
            if ((allowNegative || dist >= 0) && !optionsSet.contains(dist)) {
                optionsSet.add(dist)
            }
            attempts++
        }

        // Fallback offsets tailored to banking calculation errors (+/- 1, +/- 2, +/- 5, +/- 10)
        val fallbackOffsets = listOf(-5.0, -2.0, -1.0, 1.0, 2.0, 5.0, 10.0, 15.0, 20.0, -10.0)
        var offsetIdx = 0
        while (optionsSet.size < 5 && offsetIdx < fallbackOffsets.size) {
            val dist = cleanAnswer + fallbackOffsets[offsetIdx++]
            if (allowNegative || dist >= 0) optionsSet.add(round(dist * 100.0) / 100.0)
        }

        var mult = 25.0
        while (optionsSet.size < 5) {
            optionsSet.add(round((cleanAnswer + mult) * 100.0) / 100.0)
            mult += 10.0
        }

        val shuffledOptions = optionsSet.toList().shuffled()
        val correctIndex = shuffledOptions.indexOf(cleanAnswer)

        return ArithmeticQuestion(
            title = title,
            questionText = question,
            options = shuffledOptions.map { formatNumber(it) },
            correctIndex = correctIndex,
            explanation = explanation
        )
    }

    // =========================================================================
    // 6. REASONING: INEQUALITIES (BANK CLERK & PO PRELIMS)
    // =========================================================================
    /**
     * Exam Standard Options:
     * 0: Only Conclusion I is true
     * 1: Only Conclusion II is true
     * 2: Either Conclusion I or II is true
     * 3: Neither Conclusion I nor II is true
     * 4: Both Conclusions I and II are true
     */
    val inequalityOptions = listOf(
        "Only Conclusion I is true",
        "Only Conclusion II is true",
        "Either Conclusion I or II is true",
        "Neither Conclusion I nor II is true",
        "Both Conclusions I and II are true"
    )

    private fun generateInequality(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        // Mode selection
        // In HARD mode, we give higher weight to 'Either-Or' and multiple comma-separated statements
        val targetOutcome = when (difficulty) {
            ArithmeticDifficulty.EASY -> listOf(0, 1, 3, 4).random()
            ArithmeticDifficulty.MEDIUM -> listOf(0, 1, 2, 3, 4).random()
            ArithmeticDifficulty.HARD -> listOf(0, 1, 2, 2, 3, 4).random() // Bias towards Either-Or & CND
        }

        if (targetOutcome == 2) {
            return generateEitherOrInequality(difficulty)
        }

        return generateStandardInequality(difficulty, targetOutcome)
    }

    /**
     * Either-Or Case:
     * Condition 1 (Complementary pair for same direction with strict vs equal):
     * e.g. Statement: A >= B = C >= D
     * Conc I: A > D, Conc II: A = D
     * Since statement gives A >= D, either A > D or A = D must hold!
     *
     * Condition 2 (Opposite signs / No Relation / 3 symbols):
     * e.g. Statement: A > B < C
     * Conc I: A > C, Conc II: A <= C
     * Since relation between A and C is unknown, one of (<, =, >) must be true.
     */
    private fun generateEitherOrInequality(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        val chars = listOf("P", "Q", "R", "S", "T", "U", "V", "W").shuffled()
        val isOppositeSymbolCase = (difficulty == ArithmeticDifficulty.HARD && Random.nextBoolean())

        val statementText: String
        val c1Text: String
        val c2Text: String
        val explanation: String

        if (!isOppositeSymbolCase) {
            // Case 1: Partial relation >= or <=
            val isGreater = Random.nextBoolean()
            val sym1 = if (isGreater) "≥" else "≤"
            val symStrict = if (isGreater) ">" else "<"
            val e1 = chars[0]; val e2 = chars[1]; val e3 = chars[2]; val e4 = chars[3]

            statementText = when (difficulty) {
                ArithmeticDifficulty.HARD -> "$e1 $sym1 $e2, $e2 = $e3, $e3 $sym1 $e4"
                ArithmeticDifficulty.MEDIUM -> "$e1 $sym1 $e2 = $e3; $e3 $sym1 $e4"
                else -> "$e1 $sym1 $e2 = $e3 $sym1 $e4"
            }

            c1Text = "I. $e1 $symStrict $e4"
            c2Text = "II. $e1 = $e4"

            explanation = """
                💡 **Either-Or Rule 1 (Complementary Relation):**
                1. Combining statements gives: $e1 $sym1 $e4 ($e1 is ${if (isGreater) "greater than or equal to" else "less than or equal to"} $e4).
                2. Conclusion I: $e1 $symStrict $e4 (Alone not definitely true).
                3. Conclusion II: $e1 = $e4 (Alone not definitely true).
                4. Since both conclusions share the same elements ($e1 and $e4) and together cover both possibilities of '$sym1', **Either Conclusion I or II is true**.
            """.trimIndent()
        } else {
            // Case 2: Opposite symbols (No relation between elements)
            val e1 = chars[0]; val e2 = chars[1]; val e3 = chars[2]; val e4 = chars[3]
            statementText = when (difficulty) {
                ArithmeticDifficulty.HARD -> "$e1 > $e2, $e2 = $e3, $e3 < $e4"
                ArithmeticDifficulty.MEDIUM -> "$e1 > $e2 = $e3; $e3 < $e4"
                else -> "$e1 > $e2 = $e3 < $e4"
            }
            c1Text = "I. $e1 > $e4"
            c2Text = "II. $e1 ≤ $e4"

            explanation = """
                💡 **Either-Or Rule 2 (Three-Symbol Complementary Pair):**
                1. Between $e1 and $e4, the signs are opposite ($e1 > $e2 and $e3 < $e4).
                2. Therefore, no definite relationship can be established between $e1 and $e4.
                3. The three possible relations are: $e1 > $e4, $e1 < $e4, or $e1 = $e4.
                4. Conclusion I ($e1 > $e4) and Conclusion II ($e1 ≤ $e4) together cover all 3 possible relations (>, <, =).
                5. Hence, **Either Conclusion I or II is true**.
            """.trimIndent()
        }

        return ArithmeticQuestion(
            title = "Reasoning: Inequalities",
            questionText = "Statements:\n$statementText\n\nConclusions:\n$c1Text\n$c2Text",
            options = inequalityOptions,
            correctIndex = 2, // Either-Or
            explanation = explanation
        )
    }

    private fun generateStandardInequality(difficulty: ArithmeticDifficulty, targetOutcome: Int): ArithmeticQuestion {
        // TargetOutcome: 0 -> Only I, 1 -> Only II, 3 -> Neither, 4 -> Both
        val letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "K", "M", "N", "P", "R", "S", "T").shuffled()
        val e0 = letters[0]; val e1 = letters[1]; val e2 = letters[2]; val e3 = letters[3]; val e4 = letters[4]

        val pattern = Random.nextInt(3)
        val c1: String
        val c2: String
        val finalStatements: String
        val finalExpl: String

        when (targetOutcome) {
            4 -> { // Both true
                when (pattern) {
                    0 -> {
                        c1 = "I. $e0 > $e2"
                        c2 = "II. $e1 ≥ $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 > $e1, $e1 = $e2, $e2 ≥ $e3, $e3 = $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 > $e1 = $e2; $e2 ≥ $e3 = $e4"
                            else -> "$e0 > $e1 = $e2 ≥ $e3 = $e4"
                        }
                        finalExpl = """
                            💡 **Both Conclusions Follow:**
                            • Statement combined: $e0 > $e1 = $e2 ≥ $e3 = $e4
                            • Conclusion I ($e0 > $e2): From $e0 > $e1 = $e2, strict '>' dominates '=' ➔ $e0 > $e2 is **True**.
                            • Conclusion II ($e1 ≥ $e4): From $e1 = $e2 ≥ $e3 = $e4, the relations '=' and '≥' give $e1 ≥ $e4 ➔ **True**.
                            Hence, **Both Conclusions I and II are true**.
                        """.trimIndent()
                    }
                    1 -> {
                        c1 = "I. $e0 ≥ $e2"
                        c2 = "II. $e1 > $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 ≥ $e1, $e1 = $e2, $e2 > $e3, $e3 ≥ $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 ≥ $e1 = $e2; $e2 > $e3 ≥ $e4"
                            else -> "$e0 ≥ $e1 = $e2 > $e3 ≥ $e4"
                        }
                        finalExpl = """
                            💡 **Both Conclusions Follow:**
                            • Statement combined: $e0 ≥ $e1 = $e2 > $e3 ≥ $e4
                            • Conclusion I ($e0 ≥ $e2): From $e0 ≥ $e1 = $e2 ➔ $e0 ≥ $e2 is **True**.
                            • Conclusion II ($e1 > $e4): From $e1 = $e2 > $e3 ≥ $e4, strict '>' dominates '≥' ➔ $e1 > $e4 is **True**.
                            Hence, **Both Conclusions I and II are true**.
                        """.trimIndent()
                    }
                    else -> {
                        c1 = "I. $e0 > $e3"
                        c2 = "II. $e1 > $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 = $e1, $e1 > $e2, $e2 = $e3, $e3 > $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 = $e1 > $e2; $e2 = $e3 > $e4"
                            else -> "$e0 = $e1 > $e2 = $e3 > $e4"
                        }
                        finalExpl = """
                            💡 **Both Conclusions Follow:**
                            • Statement combined: $e0 = $e1 > $e2 = $e3 > $e4
                            • Conclusion I ($e0 > $e3): From $e0 = $e1 > $e2 = $e3 ➔ $e0 > $e3 is **True**.
                            • Conclusion II ($e1 > $e4): From $e1 > $e2 = $e3 > $e4 ➔ $e1 > $e4 is **True**.
                            Hence, **Both Conclusions I and II are true**.
                        """.trimIndent()
                    }
                }
            }
            0 -> { // Only I true
                when (pattern) {
                    0 -> {
                        c1 = "I. $e0 > $e2"
                        c2 = "II. $e1 ≥ $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 > $e1, $e1 = $e2, $e2 < $e3, $e3 ≤ $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 > $e1 = $e2; $e2 < $e3 ≤ $e4"
                            else -> "$e0 > $e1 = $e2 < $e3 ≤ $e4"
                        }
                        finalExpl = """
                            💡 **Only Conclusion I Follows:**
                            • Conclusion I ($e0 > $e2): From $e0 > $e1 = $e2, strict '>' dominates '=' ➔ $e0 > $e2 is **True**.
                            • Conclusion II ($e1 ≥ $e4): From $e1 = $e2 < $e3 ≤ $e4, we obtain $e1 < $e4. Therefore, $e1 ≥ $e4 is definitely **False**.
                            Hence, **Only Conclusion I is true**.
                        """.trimIndent()
                    }
                    1 -> {
                        c1 = "I. $e0 > $e3"
                        c2 = "II. $e2 ≥ $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 ≥ $e1, $e1 > $e2, $e2 = $e3, $e3 < $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 ≥ $e1 > $e2; $e2 = $e3 < $e4"
                            else -> "$e0 ≥ $e1 > $e2 = $e3 < $e4"
                        }
                        finalExpl = """
                            💡 **Only Conclusion I Follows:**
                            • Conclusion I ($e0 > $e3): From $e0 ≥ $e1 > $e2 = $e3, strict '>' dominates '≥' and '=' ➔ $e0 > $e3 is **True**.
                            • Conclusion II ($e2 ≥ $e4): From $e2 = $e3 < $e4, we obtain $e2 < $e4. Therefore, $e2 ≥ $e4 is definitely **False**.
                            Hence, **Only Conclusion I is true**.
                        """.trimIndent()
                    }
                    else -> {
                        c1 = "I. $e1 > $e3"
                        c2 = "II. $e0 < $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 = $e1, $e1 > $e2, $e2 ≥ $e3, $e3 < $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 = $e1 > $e2; $e2 ≥ $e3 < $e4"
                            else -> "$e0 = $e1 > $e2 ≥ $e3 < $e4"
                        }
                        finalExpl = """
                            💡 **Only Conclusion I Follows:**
                            • Conclusion I ($e1 > $e3): From $e1 > $e2 ≥ $e3, strict '>' dominates '≥' ➔ $e1 > $e3 is **True**.
                            • Conclusion II ($e0 < $e4): Between $e0 and $e4, the signs flip ($e1 > $e2 and $e3 < $e4), so no definite relation can be established ➔ **False**.
                            Hence, **Only Conclusion I is true**.
                        """.trimIndent()
                    }
                }
            }
            1 -> { // Only II true
                when (pattern) {
                    0 -> {
                        c1 = "I. $e0 > $e2"
                        c2 = "II. $e1 ≥ $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 < $e1, $e1 ≥ $e2, $e2 = $e3, $e3 ≥ $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 < $e1 ≥ $e2; $e2 = $e3 ≥ $e4"
                            else -> "$e0 < $e1 ≥ $e2 = $e3 ≥ $e4"
                        }
                        finalExpl = """
                            💡 **Only Conclusion II Follows:**
                            • Conclusion I ($e0 > $e2): Between $e0 and $e2 ($e0 < $e1 ≥ $e2), opposite symbols ('<' and '≥') exist. No definite relation can be established ➔ **False**.
                            • Conclusion II ($e1 ≥ $e4): From $e1 ≥ $e2 = $e3 ≥ $e4, combining relations gives $e1 ≥ $e4 ➔ **True**.
                            Hence, **Only Conclusion II is true**.
                        """.trimIndent()
                    }
                    1 -> {
                        c1 = "I. $e1 > $e3"
                        c2 = "II. $e2 > $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 ≤ $e1, $e1 < $e2, $e2 = $e3, $e3 > $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 ≤ $e1 < $e2; $e2 = $e3 > $e4"
                            else -> "$e0 ≤ $e1 < $e2 = $e3 > $e4"
                        }
                        finalExpl = """
                            💡 **Only Conclusion II Follows:**
                            • Conclusion I ($e1 > $e3): From $e1 < $e2 = $e3, we obtain $e1 < $e3. Therefore, $e1 > $e3 is definitely **False**.
                            • Conclusion II ($e2 > $e4): From $e2 = $e3 > $e4, combining relations gives $e2 > $e4 ➔ **True**.
                            Hence, **Only Conclusion II is true**.
                        """.trimIndent()
                    }
                    else -> {
                        c1 = "I. $e0 > $e2"
                        c2 = "II. $e2 ≥ $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 > $e1, $e1 < $e2, $e2 = $e3, $e3 ≥ $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 > $e1 < $e2; $e2 = $e3 ≥ $e4"
                            else -> "$e0 > $e1 < $e2 = $e3 ≥ $e4"
                        }
                        finalExpl = """
                            💡 **Only Conclusion II Follows:**
                            • Conclusion I ($e0 > $e2): Between $e0 and $e2 ($e0 > $e1 < $e2), opposite signs ('>' and '<') exist. No relationship can be established ➔ **False**.
                            • Conclusion II ($e2 ≥ $e4): From $e2 = $e3 ≥ $e4, we obtain $e2 ≥ $e4 ➔ **True**.
                            Hence, **Only Conclusion II is true**.
                        """.trimIndent()
                    }
                }
            }
            else -> { // Neither true (3)
                when (pattern) {
                    0 -> {
                        c1 = "I. $e0 > $e2"
                        c2 = "II. $e1 < $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 < $e1, $e1 > $e2, $e2 < $e3, $e3 > $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 < $e1 > $e2; $e2 < $e3 > $e4"
                            else -> "$e0 < $e1 > $e2 < $e3 > $e4"
                        }
                        finalExpl = """
                            💡 **Neither Conclusion Follows:**
                            • Conclusion I ($e0 > $e2): Between $e0 and $e2 ($e0 < $e1 > $e2), signs are opposite ('<' and '>'), so no relation can be established ➔ **False**.
                            • Conclusion II ($e1 < $e4): Between $e1 and $e4 ($e1 > $e2 < $e3 > $e4), signs are opposite, so no relation can be established ➔ **False**.
                            Hence, **Neither Conclusion I nor II is true**.
                        """.trimIndent()
                    }
                    1 -> {
                        c1 = "I. $e0 < $e2"
                        c2 = "II. $e1 > $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 > $e1, $e1 < $e2, $e2 > $e3, $e3 < $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 > $e1 < $e2; $e2 > $e3 < $e4"
                            else -> "$e0 > $e1 < $e2 > $e3 < $e4"
                        }
                        finalExpl = """
                            💡 **Neither Conclusion Follows:**
                            • Conclusion I ($e0 < $e2): Between $e0 and $e2 ($e0 > $e1 < $e2), signs are opposite ('>' and '<'), so no relation can be established ➔ **False**.
                            • Conclusion II ($e1 > $e4): Between $e1 and $e4 ($e1 < $e2 > $e3 < $e4), signs are opposite, so no relation can be established ➔ **False**.
                            Hence, **Neither Conclusion I nor II is true**.
                        """.trimIndent()
                    }
                    else -> {
                        c1 = "I. $e0 > $e2"
                        c2 = "II. $e2 ≤ $e4"
                        finalStatements = when (difficulty) {
                            ArithmeticDifficulty.HARD -> "$e0 ≥ $e1, $e1 < $e2, $e2 ≤ $e3, $e3 > $e4"
                            ArithmeticDifficulty.MEDIUM -> "$e0 ≥ $e1 < $e2; $e2 ≤ $e3 > $e4"
                            else -> "$e0 ≥ $e1 < $e2 ≤ $e3 > $e4"
                        }
                        finalExpl = """
                            💡 **Neither Conclusion Follows:**
                            • Conclusion I ($e0 > $e2): Between $e0 and $e2 ($e0 ≥ $e1 < $e2), signs are opposite ('≥' and '<'), so no relation can be established ➔ **False**.
                            • Conclusion II ($e2 ≤ $e4): Between $e2 and $e4 ($e2 ≤ $e3 > $e4), signs are opposite ('≤' and '>'), so no relation can be established ➔ **False**.
                            Hence, **Neither Conclusion I nor II is true**.
                        """.trimIndent()
                    }
                }
            }
        }

        return ArithmeticQuestion(
            title = "Reasoning: Inequalities",
            questionText = "Statements:\n$finalStatements\n\nConclusions:\n$c1\n$c2",
            options = inequalityOptions,
            correctIndex = targetOutcome,
            explanation = finalExpl
        )
    }

    // =========================================================================
    // 7. DIVISIBILITY RULES & REMAINDER THEOREMS
    // =========================================================================
    private fun generateDivisibilityAndRemainders(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        return when (difficulty) {
            ArithmeticDifficulty.EASY -> {
                if (Random.nextBoolean()) {
                    val d1 = Random.nextInt(1, 9)
                    val d2 = Random.nextInt(0, 9)
                    val d3 = Random.nextInt(0, 9)
                    val d4 = Random.nextInt(0, 9)
                    val currentSum = d1 + d2 + d3 + d4
                    val targetMultiple = ((currentSum / 9) + 1) * 9
                    val k = targetMultiple - currentSum
                    val kVal = if (k == 9) 0 else k
                    val questionStr = "If the 5-digit number ${d1}${d2}k${d3}${d4} is completely divisible by 9, what is the single-digit value of k?"
                    val explanation = "💡 Divisibility rule of 9: Sum of digits must be a multiple of 9.\nSum = $d1 + $d2 + k + $d3 + $d4 = ${currentSum} + k.\nThe nearest multiple of 9 is $targetMultiple.\nTherefore, k = $targetMultiple - $currentSum = $kVal."

                    buildQuestion(
                        title = "Divisibility Rules (Multiple of 9)",
                        question = questionStr,
                        answer = kVal.toDouble(),
                        distractorLogic = { ((it.toInt() + listOf(1, 2, 3, 4, 5).random()) % 10).toDouble() },
                        explanation = explanation
                    )
                } else {
                    val mod = listOf(7, 8, 9, 11).random()
                    val q1 = Random.nextInt(4, 12)
                    val q2 = Random.nextInt(5, 15)
                    val r1 = Random.nextInt(1, mod)
                    val r2 = Random.nextInt(1, mod)
                    val numA = q1 * mod + r1
                    val numB = q2 * mod + r2
                    val ans = (r1 * r2) % mod
                    val explanation = "💡 Remainder shortcut: Find individual remainders first.\n• $numA ÷ $mod leaves remainder $r1\n• $numB ÷ $mod leaves remainder $r2\nTotal Remainder = ($r1 × $r2) mod $mod = ${r1 * r2} mod $mod = $ans."

                    buildQuestion(
                        title = "Remainder Theorem (Product Shortcut)",
                        question = "What is the remainder when ($numA × $numB) is divided by $mod?",
                        answer = ans.toDouble(),
                        distractorLogic = { ((it.toInt() + listOf(1, 2, 3, 4).random()) % mod).toDouble() },
                        explanation = explanation
                    )
                }
            }
            ArithmeticDifficulty.MEDIUM -> {
                if (Random.nextBoolean()) {
                    val x = Random.nextInt(12, 28)
                    val n = Random.nextInt(15, 80)
                    val add = Random.nextInt(3, 15)
                    val ans = (1 + add) % x
                    val questionStr = "Find the remainder when (${x + 1}^$n + $add) is divided by $x."
                    val explanation = "💡 Binomial Remainder shortcut: (${x}+1)^$n ≡ 1^$n = 1 (mod $x).\nSo remainder = (1 + $add) mod $x = ${1 + add} mod $x = $ans."

                    buildQuestion(
                        title = "Remainder Theorem (Binomial Form)",
                        question = questionStr,
                        answer = ans.toDouble(),
                        distractorLogic = { ((it.toInt() + listOf(1, 2, 3, 4).random()) % x).toDouble() },
                        explanation = explanation
                    )
                } else {
                    val mod = 11
                    val q1 = Random.nextInt(3, 9)
                    val q2 = Random.nextInt(3, 9)
                    val q3 = Random.nextInt(3, 9)
                    val r1 = Random.nextInt(2, 8)
                    val r2 = Random.nextInt(2, 8)
                    val r3 = Random.nextInt(2, 8)
                    val n1 = q1 * mod + r1
                    val n2 = q2 * mod + r2
                    val n3 = q3 * mod + r3
                    val ans = (r1 * r2 * r3) % mod
                    val explanation = "💡 Step-by-step remainder breakdown:\n1. $n1 mod 11 = $r1\n2. $n2 mod 11 = $r2\n3. $n3 mod 11 = $r3\nResult = ($r1 × $r2 × $r3) mod 11 = ${r1 * r2 * r3} mod 11 = $ans."

                    buildQuestion(
                        title = "Remainder Theorem (3-Factor Expansion)",
                        question = "Find the remainder when ($n1 × $n2 × $n3) is divided by 11.",
                        answer = ans.toDouble(),
                        distractorLogic = { ((it.toInt() + listOf(1, 2, 3, 4).random()) % 11).toDouble() },
                        explanation = explanation
                    )
                }
            }
            ArithmeticDifficulty.HARD -> {
                val prime = listOf(7, 11, 13, 17).random()
                val base = listOf(2, 3, 4, 5).filter { it % prime != 0 }.random()
                val cycle = prime - 1
                val r = Random.nextInt(1, 4)
                val exponent = cycle * Random.nextInt(3, 12) + r
                var ans = 1
                for (i in 0 until r) {
                    ans = (ans * base) % prime
                }
                val explanation = "💡 Fermat's Little Theorem:\nSince $prime is prime and gcd($base, $prime) = 1, by Fermat: $base^${cycle} ≡ 1 (mod $prime).\nDivide exponent: $exponent ÷ $cycle leaves remainder $r.\nTherefore, $base^$exponent ≡ $base^$r ≡ $ans (mod $prime)."

                buildQuestion(
                    title = "Fermat's Remainder Theorem",
                    question = "What is the remainder when $base^$exponent is divided by $prime?",
                    answer = ans.toDouble(),
                    distractorLogic = { ((it.toInt() + listOf(1, 2, 3).random()) % prime).toDouble() },
                    explanation = explanation
                )
            }
        }
    }

    // =========================================================================
    // 8. SIMPLIFICATION / BODMAS WITH FRACTIONS
    // =========================================================================
    private fun generateFractionBODMAS(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        return when (difficulty) {
            ArithmeticDifficulty.EASY -> {
                val w1 = Random.nextInt(2, 6)
                val w2 = Random.nextInt(3, 8)
                val d = listOf(4, 6, 8).random()
                val num1 = Random.nextInt(1, (d / 2).coerceAtLeast(2))
                val num2 = Random.nextInt(1, (d / 2).coerceAtLeast(2))
                val mult = d * Random.nextInt(2, 5)
                val answer = (w1 + w2) * mult + (num1 + num2) * (mult / d)

                buildQuestion(
                    title = "Fraction Simplification (Mixed Numbers)",
                    question = "($w1 $num1/$d + $w2 $num2/$d) × $mult = ?",
                    answer = answer.toDouble(),
                    distractorLogic = { it + listOf(-d * 2.0, -d.toDouble(), d.toDouble(), d * 2.0).random() },
                    explanation = "💡 Fast Fraction Rule:\n1. Combine whole parts: $w1 + $w2 = ${w1 + w2}\n2. Combine fractional parts: ($num1 + $num2)/$d\n3. Multiply: (${w1 + w2}) × $mult + (${num1 + num2}) × ${mult / d} = $answer."
                )
            }
            ArithmeticDifficulty.MEDIUM -> {
                val denom = listOf(3, 4, 5, 6, 7).random()
                val numer = Random.nextInt(1, denom)
                val base = denom * Random.nextInt(8, 20)
                val divNum = listOf(2, 3, 4, 5).random()
                val ofResult = (base / denom) * numer
                val divPart = ofResult / divNum
                val addPart = Random.nextInt(10, 40)
                val answer = divPart + addPart

                buildQuestion(
                    title = "BODMAS Fractions & 'Of' Precedence",
                    question = "$numer/$denom of $base ÷ $divNum + $addPart = ?",
                    answer = answer.toDouble(),
                    distractorLogic = { it + listOf(-15.0, -10.0, 10.0, 15.0).random() },
                    explanation = "💡 BODMAS Priority (B-O-D-M-A-S):\n1. 'Of' first: $numer/$denom of $base = ($base ÷ $denom) × $numer = $ofResult\n2. Division next: $ofResult ÷ $divNum = $divPart\n3. Addition last: $divPart + $addPart = $answer."
                )
            }
            ArithmeticDifficulty.HARD -> {
                val a = Random.nextInt(3, 8)
                val b = Random.nextInt(2, 5)
                val factor = listOf(12, 16, 24, 36).random()
                val term1 = (factor * a) / b
                val term2 = Random.nextInt(15, 50)
                val answer = term1 - term2

                buildQuestion(
                    title = "Complex Fractional Simplification",
                    question = "($factor ÷ $b/$a) - $term2 = ?",
                    answer = answer.toDouble(),
                    distractorLogic = { it + listOf(-20.0, -10.0, 10.0, 20.0, 30.0).random() },
                    explanation = "💡 Division by a fraction means multiplying by its reciprocal:\n1. $factor ÷ ($b/$a) = $factor × ($a/$b) = ${factor / b} × $a = $term1\n2. Subtraction: $term1 - $term2 = $answer."
                )
            }
        }
    }

    // =========================================================================
    // 9. RATIO & PROPORTION SHORTCUTS
    // =========================================================================
    private fun generateRatioAndProportion(difficulty: ArithmeticDifficulty): ArithmeticQuestion {
        return when (difficulty) {
            ArithmeticDifficulty.EASY -> {
                val rA = Random.nextInt(2, 6)
                val rB1 = Random.nextInt(3, 7)
                val multB = Random.nextInt(1, 3)
                val rB2 = rB1 * multB
                val rC = Random.nextInt(3, 8)
                val eqA = rA * multB
                val eqB = rB2
                val eqC = rC
                val sumParts = eqA + eqB + eqC
                val unitVal = Random.nextInt(5, 25) * 10
                val totalMoney = sumParts * unitVal
                val targetAnswer = eqB * unitVal

                buildQuestion(
                    title = "Ratio Combining & Distribution",
                    question = "A sum of ₹$totalMoney is divided among A, B, and C such that A : B = $rA : $rB1 and B : C = $rB2 : $rC. What is B's share?",
                    answer = targetAnswer.toDouble(),
                    distractorLogic = { it + listOf(-unitVal * 2.0, -unitVal.toDouble(), unitVal.toDouble(), unitVal * 2.0).random() },
                    explanation = "💡 Ratio Combining Shortcut:\n• A : B = $rA : $rB1 = $eqA : $eqB\n• B : C = $rB2 : $rC = $eqB : $eqC\nCombined Ratio A : B : C = $eqA : $eqB : $eqC\nTotal parts = $eqA + $eqB + $eqC = $sumParts parts = ₹$totalMoney.\n1 part = ₹$unitVal.\nB's share ($eqB parts) = $eqB × ₹$unitVal = ₹$targetAnswer."
                )
            }
            ArithmeticDifficulty.MEDIUM -> {
                if (Random.nextBoolean()) {
                    val rootA = Random.nextInt(3, 12)
                    val rootB = Random.nextInt(4, 15)
                    val a = rootA * rootA
                    val b = rootB * rootB
                    val meanProp = rootA * rootB

                    buildQuestion(
                        title = "Mean Proportional Shortcut",
                        question = "Find the mean proportional between $a and $b.",
                        answer = meanProp.toDouble(),
                        distractorLogic = { it + listOf(-10.0, -5.0, 5.0, 10.0).random() },
                        explanation = "💡 Mean Proportional Formula: x = √(a × b).\nHere, x = √($a × $b) = √$a × √$b = $rootA × $rootB = $meanProp."
                    )
                } else {
                    val k = Random.nextInt(2, 6)
                    val a = k * Random.nextInt(2, 5)
                    val b = a * Random.nextInt(2, 4)
                    val thirdProp = (b * b) / a

                    buildQuestion(
                        title = "Third Proportional Formula",
                        question = "Find the third proportional to $a and $b.",
                        answer = thirdProp.toDouble(),
                        distractorLogic = { it + listOf(-b.toDouble(), b.toDouble(), (b * 2).toDouble()).random() },
                        explanation = "💡 Third Proportional Formula: If a : b = b : x, then x = b² ÷ a.\nx = $b² ÷ $a = ${b * b} ÷ $a = $thirdProp."
                    )
                }
            }
            ArithmeticDifficulty.HARD -> {
                val unit = Random.nextInt(10, 40) * 10
                val p = 5
                val q = 4
                val r = 3
                val s = 2
                val savings = unit * 2
                val xVal = savings / 2
                val answerA = p * xVal

                buildQuestion(
                    title = "Income & Expenditure (Cross-Product Method)",
                    question = "The ratio of monthly incomes of A and B is $p : $q and the ratio of their expenditures is $r : $s. If each saves ₹$savings per month, what is the monthly income of A?",
                    answer = answerA.toDouble(),
                    distractorLogic = { it + listOf(-xVal.toDouble(), xVal.toDouble(), xVal * 2.0).random() },
                    explanation = "💡 Banking Cross-Multiplication Shortcut:\nIncome Ratio: $p : $q\nExpenditure Ratio: $r : $s\nSavings: ₹$savings each.\nCross-difference: (4 × $r) - (5 × $s) = ${4 * r} - ${5 * s} = 2 units.\n2 units = ₹$savings ➔ 1 unit = ₹$xVal.\nA's income = $p units = $p × ₹$xVal = ₹$answerA."
                )
            }
        }
    }
}

