package com.thesisapp.utils

import kotlin.random.Random

object CodeGenerator {
    private val alphabet = (('A'..'Z') + ('0'..'9')).toCharArray()

    fun code(length: Int = 6): String {
        return (1..length).map { alphabet[Random.nextInt(alphabet.size)] }.joinToString("")
    }
}

