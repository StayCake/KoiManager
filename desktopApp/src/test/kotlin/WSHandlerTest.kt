import com.koisv.kcdesktop.WSHandler
import com.koisv.kcdesktop.wsDebug
import io.ktor.websocket.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.PrivateKey
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalEncodingApi
class WSHandlerTest {

    @Before
    fun setUp() {
        wsDebug = true
        MockKAnnotations.init(this)
        mockkObject(WSHandler)
    }

    @Test
    fun testGetKeys() {
        val mockFile = mockk<File>()
        every { mockFile.exists() } returns true
        every { mockFile.listFiles() } returns arrayOf(File("key1.pem"), File("key2.pem"))

        val keys = WSHandler.getKeys()
        assertEquals(2, keys.size)
        assertTrue(keys.all { it.extension == "pem" })
    }

    @Test
    fun testOtpRequest() = runTest {
        coEvery { WSHandler.wsSession.send(any<Frame.Text>()) } just Runs
        coEvery { WSHandler.responses["otp"] } returns true

        val result = WSHandler.otpRequest()
        assertTrue(result)
    }

    @Test
    fun testSendRegister() = runTest {
        coEvery { WSHandler.wsSession.send(any<Frame.Text>()) } just Runs
        coEvery { WSHandler.responses["register"] } returns 0
        coEvery { WSHandler.responses["key"] } returns mockk<PrivateKey>()

        val result = WSHandler.sendRegister("testId", "testNickname", "testOtp")
        assertEquals(0, result)
    }

    @Test
    fun testSendLogin() = runTest {
        coEvery { WSHandler.wsSession.send(any<Frame.Text>()) } just Runs
        coEvery { WSHandler.responses["login"] } returns true
        coEvery { WSHandler.responses["key_login"] } returns mockk<PrivateKey>()

        val result = WSHandler.sendLogin("testId", File("key.pem"))
        assertEquals(0, result)
    }

    @Test
    fun testSendMessage() = runTest {
        coEvery { WSHandler.wsSession.send(any<Frame.Text>()) } just Runs

        val result = WSHandler.sendMessage("testMessage", "receiverId")
        assertTrue(result)
    }
}