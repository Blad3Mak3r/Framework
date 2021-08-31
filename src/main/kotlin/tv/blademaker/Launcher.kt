/*******************************************************************************
 * Copyright (c) 2021. Blademaker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 ******************************************************************************/

package tv.blademaker

import com.typesafe.config.ConfigException
import com.xenomachina.argparser.ArgParser
import dev.minn.jda.ktx.CoroutineEventManager
import dev.minn.jda.ktx.listener
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.LoggerFactory
import tv.blademaker.extensions.jda.Intents
import tv.blademaker.i18n.I18n
import tv.blademaker.slash.handler.DefaultSlashCommandHandler
import tv.blademaker.slash.handler.SlashCommandHandler
import tv.blademaker.utils.*
import tv.blademaker.utils.ShardingStrategy.Companion.setShardingStrategy
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.security.auth.login.LoginException
import kotlin.properties.Delegates

@Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")
object Launcher : BaseBot {

    lateinit var shardingStrategy: ShardingStrategy
        private set

    private lateinit var shardManager: ShardManager

    var pid by Delegates.notNull<Long>()
        private set

    lateinit var slashCommandHandler: SlashCommandHandler
        private set

    private val RESTACTION_DEFAULT_FAILURE = RestAction.getDefaultFailure()

    @Throws(LoginException::class, ConfigException::class)
    fun init(args: ApplicationArguments) {
        pid = ProcessHandle.current().pid()

        shardingStrategy = ShardingStrategy.create(args)

        RestAction.setPassContext(false)
        RestAction.setDefaultTimeout(7, TimeUnit.SECONDS)
        RestAction.setDefaultFailure {
            if (it !is TimeoutException) {
                RESTACTION_DEFAULT_FAILURE.accept(it)
            }
        }

        Utils.printBanner(pid, log)

        //Initialize sentry
        SentryUtils.init()

        I18n.init()


        slashCommandHandler = DefaultSlashCommandHandler("tv.blademaker.commands")

        shardManager = DefaultShardManagerBuilder.createLight(Credentials.token).apply {
            setHttpClient(HttpUtils.client)
            setActivityProvider { Activity.competing("Valorant /help") }
            setEventManagerProvider {
                val executor = Utils.newCoroutineDispatcher("event-manager-worker-%d", 4, 20, 6L, TimeUnit.MINUTES)
                CoroutineEventManager(CoroutineScope(executor + SupervisorJob()))
            }

            setCompression(Compression.ZLIB)

            disableIntents(Intents.allIntents)
            enableIntents(Intents.enabledIntents)

            enableCache(CacheFlag.MEMBER_OVERRIDES)
            disableCache(
                CacheFlag.VOICE_STATE,
                CacheFlag.ACTIVITY,
                CacheFlag.EMOTE,
                CacheFlag.CLIENT_STATUS,
                CacheFlag.ONLINE_STATUS,
                CacheFlag.ROLE_TAGS
            )

            setBulkDeleteSplittingEnabled(false)
            setChunkingFilter(ChunkingFilter.NONE)
            setMemberCachePolicy(MemberCachePolicy.NONE)

            setShardingStrategy(shardingStrategy)
        }.build()

        shardManager.listener<GenericEvent> { event ->
            when (event) {
                is SlashCommandEvent -> slashCommandHandler.onSlashCommandEvent(event)
                // TODO: Your listeners here
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            Thread.currentThread().name = "shutdown-thread"

            shutdown(0)
        })
    }

    @JvmStatic
    @Throws(LoginException::class, ConfigException::class)
    fun main(args: Array<String>) {
        val parsedArgs = ArgParser(args).parseInto(::ApplicationArguments)

        init(parsedArgs)
    }

    override fun shutdown(code: Int) {
        try {
            log.info("Shutting down Killjoy with code $code...")

            HttpUtils.shutdown()

            for (jda in shardManager.shardCache) {
                shardManager.shutdown(jda.shardInfo.shardId)
            }

            shardManager.shutdown()
        } catch (e: Exception) {
            Sentry.captureException(e)
            log.error("Exception shutting down Killjoy.", e)
            Runtime.getRuntime().halt(code)
        }
    }

    override fun getShardManager(): ShardManager {
        return shardManager
    }

    private val log = LoggerFactory.getLogger(Launcher::class.java)
}