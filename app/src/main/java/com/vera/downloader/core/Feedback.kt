package com.vera.downloader.core

import android.util.Base64

/**
 * Endereço montado em partes para não ficar em texto claro no APK nem na interface.
 * Não é criptografia forte; serve para evitar exposição acidental.
 */
object Feedback {
    private val chunks = listOf(
        "c3VwcG9y",
        "dEB2ZXJh",
        "ZG93bmxv",
        "YWRlci5j",
        "b20="
    )

    fun recipient(): String = chunks.joinToString("") { chunk ->
        String(Base64.decode(chunk, Base64.NO_WRAP), Charsets.UTF_8)
    }
}
