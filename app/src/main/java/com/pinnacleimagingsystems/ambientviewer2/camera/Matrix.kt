package com.pinnacleimagingsystems.ambientviewer2.camera

import android.util.Rational

class Matrix(elements: Array<Rational>) {
    val elements = elements.copyOf()

    override fun toString(): String {
        val e = elements.map { it.toFloat() }
        return "Matrix(${e[0]} ${e[1]} ${e[2]}, ${e[3]} ${e[4]} ${e[5]}, ${e[6]} ${e[7]} ${e[8]})"
    }
}
