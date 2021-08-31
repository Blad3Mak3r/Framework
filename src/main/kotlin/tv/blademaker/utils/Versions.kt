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

package tv.blademaker.utils

import net.dv8tion.jda.api.JDAInfo
import java.util.*

object Versions {
    private val appProps = ResourceBundle.getBundle("app")

    val BOT: String = if (appProps.getString("version").startsWith("@")) "UNKNOWN" else appProps.getString("version")
    val COMMIT: String = if (appProps.getString("revision").startsWith("@")) "UNKNOWN" else appProps.getString("revision")
    val BUILD_NUMBER: String = if (appProps.getString("build").startsWith("@")) "UNKNOWN" else appProps.getString("build")
    val JDA: String = JDAInfo.VERSION
}