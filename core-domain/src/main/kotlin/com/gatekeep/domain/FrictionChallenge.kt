package com.gatekeep.domain

import com.gatekeep.domain.model.FrictionDifficulty
import com.gatekeep.domain.model.MathChallenge
import kotlin.random.Random

object FrictionChallenge {

    fun generate(difficulty: FrictionDifficulty, random: Random = Random.Default): MathChallenge {
        return when (difficulty) {
            FrictionDifficulty.easy -> {
                val a = random.nextInt(1, 10)
                val b = random.nextInt(1, 10)
                MathChallenge("$a + $b = ?", a + b, difficulty)
            }
            FrictionDifficulty.medium -> {
                val a = random.nextInt(10, 50)
                val b = random.nextInt(10, 50)
                MathChallenge("$a + $b = ?", a + b, difficulty)
            }
            FrictionDifficulty.hard -> {
                val a = random.nextInt(5, 15)
                val b = random.nextInt(5, 15)
                val c = random.nextInt(10, 30)
                MathChallenge("$a × $b + $c = ?", a * b + c, difficulty)
            }
        }
    }

    fun verify(challenge: MathChallenge, answer: Int): Boolean = challenge.answer == answer

    const val HOLD_BUTTON_SECONDS = 10
    const val DEFAULT_PHRASE = "I choose to stop"
}
