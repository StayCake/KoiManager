package com.koisv.dkm

import discord4j.common.JacksonResources
import discord4j.discordjson.json.ApplicationCommandRequest
import discord4j.rest.RestClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.*
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.isRegularFile
import kotlin.io.path.name


open class CmdReg(private val restClient: RestClient) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java) as Logger
    }

    //Since this will only run once on startup, blocking is okay.
    @Throws(IOException::class)
    fun registerCommands() {
        //Create an ObjectMapper that supports Discord4J classes
        val d4jMapper = JacksonResources.create()

        // Convenience variables for the sake of easier to read code below
        val applicationService = restClient.applicationService
        val applicationId : Long = restClient.applicationId.block() ?: 0

        //Get our commands json from resources as command data
        val commands: MutableList<ApplicationCommandRequest> = ArrayList()
        for (json in getCommandsJson()) {
            val request = d4jMapper.objectMapper
                .readValue(json, ApplicationCommandRequest::class.java)
            commands.add(request) //Add to our array list
        }
        /* Bulk overwrite commands. This is now idempotent, so it is safe to use this even when only 1 command
        is changed/added/removed*/
        applicationService.bulkOverwriteGlobalApplicationCommand(applicationId, commands)
            .doOnNext { logger.debug("[${it.name()}] 명령어 등록 완료.") }
            .doOnError { e: Throwable? ->
                logger.error("[/] 등록 오류 발생 : ", e)
            }
            .subscribe()
    }

    /* The two below methods are boilerplate that can be completely removed when using Spring Boot */
    @Throws(IOException::class)
    private fun getCommandsJson(): List<String> {
        //The name of the folder the commands json is in, inside our resources folder
        val commandsFolderName = "commands/"

        //Get the folder as a resource

        val uri: URI? = this::class.java.getResource("/$commandsFolderName")?.toURI()
        val verfURI = Objects.requireNonNull(uri, "$commandsFolderName could not be found") ?: return listOf()
        val loc: Path = if (verfURI.scheme.equals("jar")) {
            val fileSystem: FileSystem = FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            fileSystem.getPath("/$commandsFolderName")
        } else {
            Paths.get(verfURI)
        }
        //Get all the files inside this folder and return the contents of the files as a list of strings
        val jsons = mutableListOf<String>()
        val paths = Files.walk(loc)
        paths.toList().filter { it.isRegularFile() }.forEach {
            if (it.parent.name != "commands") jsons.add(it.parent.name + "/" + it.name) else jsons.add(it.toFile().name)
        }
        val list: MutableList<String> = ArrayList()
        for (file in jsons) {
            val resourceFileAsString = getResourceFileAsString(commandsFolderName + file)
            Objects.requireNonNull(resourceFileAsString, "Command file not found: $file")?.let { list.add(it) }
        }
        return list
    }

    @Throws(IOException::class)
    private fun getResourceFileAsString(fileName: String): String? {
        val classLoader = javaClass.classLoader //ClassLoader.getSystemClassLoader()
        classLoader.getResourceAsStream(fileName).use { resourceAsStream ->
            if (resourceAsStream == null) return null
            InputStreamReader(resourceAsStream, Charset.forName("UTF-8")).use { inputStreamReader ->
                BufferedReader(inputStreamReader).use { reader ->
                    return reader.lines().collect(Collectors.joining(System.lineSeparator()))
                }
            }
        }
    }
}