package ltd.matrixstudios.application

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.LongSerializationPolicy
import ltd.matrixstudios.application.priority.PriorityQueueComparator
import ltd.matrixstudios.application.queues.Queue
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import java.net.URI
import java.util.*

object FunnelCommons {

    lateinit var instance: FunnelCommons

    var gson: Gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .setLongSerializationPolicy(LongSerializationPolicy.STRING)
        .create()

    lateinit var globalJedis: JedisPool
    lateinit var globalJedisResource: Jedis

    lateinit var globalQueueForInstance: Queue

    fun start(jedisURI: String, queueId: String, destination: String) {
        instance = this

        globalJedis = JedisPool(URI(jedisURI))
        globalJedisResource = globalJedis.resource

        runRedisCommand {
            val exists = it.exists("Funnel:queues:$queueId")

            if (exists) {
                val redisFetchedData = it.hget("Funnel:queues:", queueId)

                val queue = gson.fromJson(redisFetchedData, Queue::class.java)

                globalQueueForInstance = queue

            }

            if (!exists) {
                val queue = Queue(queueId.lowercase(), PriorityQueue(PriorityQueueComparator()), destination, queueId, true)

                queue.save()
            }
        }
    }

    fun <T> runRedisCommand(runnable: (Jedis) -> T)  : T {
        if (globalJedis == null || globalJedis.isClosed) {
            throw InterruptedException("Running of command was interrupted by the JedisPool not existing")
        }

        globalJedisResource.use { jedis -> return runnable(jedis) }
    }
}