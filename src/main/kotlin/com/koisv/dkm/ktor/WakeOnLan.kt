package com.koisv.dkm.ktor

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.Logger

private const val PORT: Int = 9

class WakeOnLan(private val logger: Logger) {
    private val selMan = SelectorManager(Dispatchers.IO)
    suspend fun magic(ip: String, mac: String): Boolean {
        try {
            val macBytes = getMacBytes(mac)
            val bytes = ByteArray(6 + 16 * macBytes.size)
            for (i in 0..5) {
                bytes[i] = 0xff.toByte()
            }
            var i = 6
            while (i < bytes.size) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
                i += macBytes.size
            }

            val address = InetSocketAddress(ip, PORT)
            val packet = Datagram(ByteReadPacket(bytes, 0, bytes.size), address)
            val socket = aSocket(selMan).udp().bind(address)
            withContext(Dispatchers.IO) {
                socket.send(packet)
                socket.close()
            }
            return true
        } catch (e: Exception) {
            logger.error(e)
            return false
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun getMacBytes(macStr: String): ByteArray {
        val bytes = ByteArray(6)
        val hex = macStr.split("([:\\-])".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        require(hex.size == 6) { "Invalid MAC address." }
        try {
            for (i in 0..5) { bytes[i] = hex[i].toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid hex digit in MAC address.")
        }
        return bytes
    }
}