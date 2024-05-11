package me.senseiwells.replay.viewer.packhost

import com.google.common.hash.Hashing
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * This class serves to host resource packs on the Server
 * and allows clients to freely request any hosted pack.
 *
 * This is useful to allow for *dynamic* pack hosting
 * as you are able to add your own packs during runtime
 * and further see what packs are available, and all the
 * relevant information, such as the hosted URL and hash.
 *
 * @param threads The number of threads used for pack hosting.
 * @see HostedPack
 * @see ReadablePack
 */
class PackHost(
    /**
     * The number of threads used for pack hosting.
     *
     * If you expect a high number of requests for packs
     * or if your packs take longer to read (e.g. they
     * have a large file size), then
     * your number of threads should be larger.
     */
    private val threads: Int = 3
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val hosted = ConcurrentHashMap<String, HostedPack>()
    private val builder = ThreadFactoryBuilder().setNameFormat("Pack-Host-%d").build()
    private val packs = ArrayList<ReadablePack>()

    private var server: HttpServer? = null
    private var pool: ExecutorService? = null
    private var future: CompletableFuture<Boolean>? = null

    val running: Boolean
        get() = this.server != null

    /**
     * Adds a [ReadablePack] to the pack host.
     *
     * If you add pack after you have already started
     * the pack host, you will need to re-start it.
     * You can simply do this by calling [start].
     *
     * @param packs The supplier to add.
     */
    fun addPack(packs: ReadablePack) {
        this.packs.add(packs)
    }

    /**
     * This gets the details for a pack that is being
     * hosted by this pack host.
     * This provides the [ReadablePack], url, and hash
     * for the pack.
     *
     * @param name The name of the hosted pack.
     * @return The [HostedPack] containing all the details.
     * @see HostedPack
     */
    fun getHostedPack(name: String): HostedPack? {
        val zipped = if (name.endsWith(".zip")) name else "$name.zip"
        return this.hosted[zipped]
    }

    /**
     * This starts or restarts the pack host.
     *
     * This will create all the [HostedPack]s from all the packs that have been
     * added to the pack host previously by the [addPack] method.
     *
     * @param hostIp The hosts IP address, this is used to create the links for the [HostedPack].
     * @param hostPort The port that the pack host should run on.
     * @param randomise Whether the pack file-names should be randomised.
     * @return A [CompletableFuture] which will complete after all packs have been loaded
     * and are ready to be requested by a client, it will be true if the host was successful.
     */
    fun start(hostIp: String? = null, hostPort: Int = 24464, randomise: Boolean = false): CompletableFuture<Boolean> {
        this.future?.cancel(true)

        val future = CompletableFuture.supplyAsync {
            val restart = this.server !== null
            this.hosted.clear()
            this.server?.stop(0)
            this.pool?.shutdownNow()
            try {
                this.logger.info("${if (restart) "Restarting" else "Starting"} ResourcePackHost...")
                var ip = hostIp
                if (ip === null) {
                    this.logger.info("No IP address found for ResourcePackHost, using localhost instead")
                    ip = InetAddress.getLocalHost().hostAddress
                }

                val server = HttpServer.create(InetSocketAddress("0.0.0.0", hostPort), 0)
                this.pool = Executors.newFixedThreadPool(this.threads, this.builder)
                server.executor = this.pool

                val futures = LinkedList<CompletableFuture<Void>>()
                for (pack in this.packs) {
                    futures.add(CompletableFuture.runAsync({
                        val sub = if (randomise) Random.nextInt(Int.MAX_VALUE).toString() else pack.name

                        val url = "http://${ip}:${hostPort}/${sub}"

                        @Suppress("DEPRECATION")
                        val hash = Hashing.sha1().hashBytes(pack.stream().use {
                            it.readBytes()
                        }).toString()

                        val hosted = HostedPack(pack, url, hash)
                        val zipped = if (pack.name.endsWith(".zip")) pack.name else "${pack.name}.zip"
                        this.hosted[zipped] = hosted

                        server.createContext("/$sub", Handler(pack))

                        this.logger.info("Hosting pack: ${pack.name}: $url")
                    }, this.pool))
                }

                // Wait for all of them to complete
                futures.forEach { it.join() }

                server.start()
                this.server = server
                this.logger.info("ResourcePackHost successfully started")
                true
            } catch (e: Exception) {
                this.logger.error("Failed to start the ResourcePackHost!", e)
                false
            }
        }
        this.future = future
        return future
    }

    /**
     * This method shuts down the pack host.
     *
     * Clients will no longer be able to request packs.
     */
    fun shutdown() {
        this.server?.stop(0)
        this.pool?.shutdownNow()
        this.future?.cancel(true)

        this.packs.clear()
        this.hosted.clear()
    }

    private inner class Handler(val pack: ReadablePack): HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if ("GET" == exchange.requestMethod && this.pack.readable()) {
                exchange.responseHeaders.add("User-Agent", "Java/ResourcePackHost")
                exchange.sendResponseHeaders(200, this.pack.length())
                exchange.responseBody.use { response ->
                    this.pack.stream().use { stream ->
                        stream.transferTo(response)
                    }
                }
            } else {
                exchange.sendResponseHeaders(400, 0)
            }
        }
    }
}