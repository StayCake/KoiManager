package com.koisv.dkm.ktor

import io.ktor.server.application.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi

@ExperimentalEncodingApi
@ExperimentalUuidApi
@DelicateCoroutinesApi
fun Application.module() {
    configureRouting()
}