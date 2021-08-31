package tv.blademaker.slash

import tv.blademaker.i18n.I18nKey
import io.sentry.Sentry
import org.slf4j.LoggerFactory
import tv.blademaker.i18n.i18n
import tv.blademaker.slash.annotations.Permissions
import tv.blademaker.slash.annotations.SlashSubCommand
import tv.blademaker.slash.utils.SlashUtils
import tv.blademaker.utils.SentryUtils
import java.util.function.Predicate
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

abstract class AbstractSlashCommand(val commandName: String) {

    private val checks: MutableList<Predicate<SlashCommandContext>> = mutableListOf()

    private val subCommands: List<SubCommand> = this::class.functions
        .filter { it.hasAnnotation<SlashSubCommand>() && it.visibility == KVisibility.PUBLIC && !it.isAbstract }
        .map { SubCommand(it) }

    private fun doChecks(ctx: SlashCommandContext): Boolean {
        if (checks.isEmpty()) return true
        return checks.all { it.test(ctx) }
    }

    private suspend fun handleSubcommand(ctx: SlashCommandContext): Boolean {
        val subCommandGroup = ctx.event.subcommandGroup

        val subCommandName = ctx.event.subcommandName
            ?: return false

        try {
            val subCommand = subCommands
                .filter { if (subCommandGroup != null) it.groupName == subCommandGroup else true }
                .find { s -> s.name.equals(subCommandName, true) }

            if (subCommand == null) {
                LOGGER.warn("Not found any valid handle for option \"$subCommandName\", executing default handler.")
                return false
            }

            LOGGER.debug("Executing \"${subCommand.name}\" for option \"$subCommandName\"")

            try {
                if (!SlashUtils.hasPermissions(ctx, subCommand.permissions)) return true

                subCommand.execute(this, ctx)
            } catch (e: Exception) {
                SentryUtils.captureSlashCommandException(ctx, e, LOGGER)

                return true
            }
            return true
        } catch (e: Exception) {
            LOGGER.error("Exception getting KFunctions to handle subcommand $subCommandName", e)
            Sentry.captureException(e)
            return false
        }
    }

    open suspend fun execute(ctx: SlashCommandContext) {
        if (!doChecks(ctx)) return
        if (handleSubcommand(ctx)) return

        handle(ctx)
    }

    internal open suspend fun handle(ctx: SlashCommandContext) {
        ctx.reply(ctx.i18n(I18nKey.COMMAND_NOT_IMPLEMENTED)).queue()
    }

    class SubCommand private constructor(
        private val handler: KFunction<*>,
        private val annotation: SlashSubCommand,
        val permissions: Permissions?
    ) {

        constructor(f: KFunction<*>) : this(
            handler = f,
            annotation = f.findAnnotation<SlashSubCommand>()!!,
            permissions = f.findAnnotation<Permissions>()
        )

        val name: String
            get() = annotation.name.takeIf { it.isNotBlank() } ?: handler.name

        val groupName: String
            get() = annotation.group

        suspend fun execute(instance: AbstractSlashCommand, ctx: SlashCommandContext) = handler.callSuspend(instance, ctx)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(AbstractSlashCommand::class.java)
    }
}