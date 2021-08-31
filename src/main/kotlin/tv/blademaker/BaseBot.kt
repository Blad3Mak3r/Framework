package tv.blademaker

import net.dv8tion.jda.api.sharding.ShardManager

interface BaseBot {

    fun getShardManager(): ShardManager

    fun getTotalGuilds(): Int {
        return getShardManager().guildCache.size().toInt()
    }

    fun getGuildsByShard(shardId: Int): Int {
        return getShardManager().shardCache.getElementById(shardId)!!.guildCache.size().toInt()
    }

    fun getTotalMembers(): Int {
        val collection = getShardManager().guildCache.map { it.memberCount }

        return if (collection.isNotEmpty()) collection.reduce { acc, i -> acc+i }
        else 0
    }

    fun getTotalMembersByShard(shardId: Int): Int {
        val collection = getShardManager().shardCache.getElementById(shardId)!!.guildCache.map { it.memberCount }

        return if (collection.isNotEmpty()) collection.reduce { acc, i -> acc+i }
        else 0
    }

    fun shutdown(code: Int)

}