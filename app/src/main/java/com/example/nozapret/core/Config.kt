package com.example.nozapret.core

object Config {
    const val DEFAULT_PROXY_HOST = "127.0.0.1"
    const val DEFAULT_PROXY_PORT = "1080"

    val BYPASS_LISTS = listOf(
        "Russia Default" to emptyArray<String>(),
        "Aggressive" to arrayOf("--drop-sack", "--mod-http", "h,d"),
    )

    data class Strategy(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val defaultArgs: String = ""
    )

    val STRATEGIES_DATA = listOf(
        Strategy("auto", "Auto (Recommended)", "Adaptive multi-stage bypass (Split, Disorder, TLS Fragment, Fake SNI).", "General"),
        Strategy("modern_ultra", "Modern Ultra", "Aggressive combination of TLS fragmentation and multi-stage desync.", "Modern"),
        Strategy("youtube_google", "YouTube/Google Fix", "Ultimate Bypass: TCP Split + TLS Fragment + QUIC Block.", "YouTube"),
        Strategy("discord_udp", "Discord/UDP Fix", "Advanced UDP/QUIC desync with multi-packet fake sequences.", "Discord"),
        Strategy("youtube_light", "YouTube Light (Battery)", "Very light strategy that drains the battery less.", "YouTube"),
        Strategy("youtube_tls_split", "YouTube TLS Split", "Strategy using TLS record splitting and multi-stage disorder.", "YouTube"),
        Strategy("ru_discord_alt", "RU Discord (Alt)", "Alternative Discord bypass using fake SNI and auto-modes.", "Discord"),
        Strategy("ru_universal_powerful", "RU Universal (Powerful)", "Aggressive bypass for most blocked sites in Russia.", "General"),
        Strategy("simple_split", "Simple Split", "Basic TCP splitting at 2nd byte.", "Simple"),
        Strategy("simple_fake", "Simple Fake", "Basic fake packet with default TTL.", "Simple"),
        Strategy("torrent_p2p", "Torrent/P2P Fix", "Optimized for P2P: UDP fakes and aggressive TCP splitting.", "Other"),
        Strategy("ru_discord_alt2", "RU Discord (Alt 2)", "Advanced Discord strategy with randomized UDP fakes and OOB splitting.", "Discord"),
        Strategy("discord_intense", "Discord UDP (Intense)", "Aggressive Discord strategy with multiple fake packets and multi-stage disorder.", "Discord"),
        Strategy("discord_mobile", "Discord Mobile (Optimized)", "Lightweight Discord bypass optimized for mobile networks.", "Discord"),
        Strategy("fake_discord_intense", "Simple Fake Discord Intense", "Simple fake variant of the Intense Discord strategy.", "Simple Fake"),
        Strategy("fake_discord_mobile", "Simple Fake Discord Mobile", "Simple fake variant of the Mobile Discord strategy.", "Simple Fake"),
        Strategy("modern_youtube_extreme", "Modern YouTube (Extreme)", "Aggressive YouTube bypass using multiple desync techniques.", "YouTube"),
        Strategy("universal_web_safe", "Universal Web (Safe)", "Safe general bypass strategy for standard web browsing.", "General"),
        Strategy("custom", "Custom", "Use arguments from General settings.", "Custom"),
        Strategy("s1", "Strategy 1", "Legacy strategy 1 from byedpi presets.", "Legacy"),
        Strategy("s2", "Strategy 2", "Legacy strategy 2 from byedpi presets.", "Legacy"),
        Strategy("s3", "Strategy 3", "Legacy strategy 3 from byedpi presets.", "Legacy"),
        Strategy("s4", "Strategy 4", "Legacy strategy 4 from byedpi presets.", "Legacy"),
        Strategy("s5", "Strategy 5", "Legacy strategy 5 from byedpi presets.", "Legacy"),
        Strategy("s6", "Strategy 6", "Legacy strategy 6 from byedpi presets.", "Legacy"),
        Strategy("s7", "Strategy 7", "Legacy strategy 7 from byedpi presets.", "Legacy"),
        Strategy("s8", "Strategy 8", "Legacy strategy 8 from byedpi presets.", "Legacy"),
        Strategy("s9", "Strategy 9", "Legacy strategy 9 from byedpi presets.", "Legacy")
    )

    val STRATEGIES = STRATEGIES_DATA.map { it.name to it.description }

    fun getStrategyById(id: String): Strategy? = STRATEGIES_DATA.find { it.id == id }
    fun getStrategyByName(name: String): Strategy? = STRATEGIES_DATA.find { it.name == name }

    fun getStrategyArgs(name: String, customArgs: String = "", fakeSniPool: List<String> = emptyList()): Array<String> {
        val baseArgs = when (name) {
            "Auto (Recommended)" -> arrayOf(
                "--tlsrec", "1+s",
                "--disorder", "1",
                "--split", "2",
                "--fake-sni", "www.google.com",
                "--fake-tls-mod", "orig",
                "--mod-http", "h,d",
                "--udp-fake", "3",
                "--wait-send",
                "--auto", "t,s,c",
                "--drop-sack",
                "--block-quic"
            )
            "Modern Ultra" -> arrayOf(
                "--tlsrec", "1+s",
                "--disorder", "1",
                "--oob", "1",
                "--split", "2",
                "--wait-send",
                "--udp-fake", "5",
                "--drop-sack",
                "--auto", "t,r,s,c",
                "--block-quic"
            )
            "YouTube/Google Fix" -> arrayOf(
                "--tlsrec", "1+s",
                "--disorder", "1",
                "--oob", "1",
                "--split", "2",
                "--fake-sni", "www.google.com",
                "--fake-sni", "www.youtube.com",
                "--fake-sni", "yandex.ru",
                "--fake-tls-mod", "orig",
                "--mod-http", "h,d",
                "--udp-fake", "15",
                "--drop-sack",
                "--wait-send",
                "--await-int", "20",
                "--auto", "t,r,s,c",
                "--block-quic"
            )
            "Discord/UDP Fix" -> arrayOf(
                "--disorder", "1",
                "--split", "1",
                "--udp-fake", "3",
                "--wait-send",
                "--drop-sack"
            )
            "YouTube Light (Battery)" -> arrayOf(
                "--tlsrec", "1+s",
                "--split", "1",
                "--auto", "t",
                "--block-quic"
            )
            "YouTube TLS Split" -> parseCustomArgs("--tlsrec 1+s --split 1 --wait-send --block-quic")
            "RU Discord (Alt)" -> arrayOf("--disorder", "1", "--split", "1", "--wait-send", "--udp-fake", "5")
            "RU Universal (Powerful)" -> arrayOf(
                "--tlsrec", "1+s",
                "--disorder", "1",
                "--split", "1",
                "--wait-send",
                "--udp-fake", "5",
                "--auto", "t,r,s,c",
                "--drop-sack",
                "--block-quic"
            )
            "Simple Split" -> arrayOf("--split", "1", "--wait-send")
            "Simple Fake" -> arrayOf("--tlsrec", "1+s", "--fake-sni", "yandex.ru")
            "Torrent/P2P Fix" -> arrayOf("--split", "1", "--udp-fake", "10", "--wait-send", "--drop-sack")
            "RU Discord (Alt 2)" -> arrayOf("--disorder", "1", "--oob", "1", "--udp-fake", "20", "--wait-send")
            "Discord UDP (Intense)" -> arrayOf(
                "--disorder", "1",
                "--oob", "1",
                "--udp-fake", "40",
                "--wait-send",
                "--await-int", "10",
                "--drop-sack"
            )
            "Discord Mobile (Optimized)" -> arrayOf(
                "--split", "1",
                "--udp-fake", "2",
                "--wait-send",
                "--auto", "s"
            )
            "Simple Fake Discord Intense" -> arrayOf("--tlsrec", "1+s", "--fake-sni", "discord.com", "--udp-fake", "10")
            "Simple Fake Discord Mobile" -> arrayOf("--tlsrec", "1+s", "--fake-sni", "discord.gg")
            "Modern YouTube (Extreme)" -> arrayOf(
                "--tlsrec", "1+s",
                "--disorder", "1",
                "--split", "1",
                "--oob", "1",
                "--mod-http", "h,d",
                "--udp-fake", "30",
                "--wait-send",
                "--await-int", "20",
                "--drop-sack",
                "--auto", "t,r,s,c",
                "--block-quic"
            )
            "Universal Web (Safe)" -> arrayOf("--split", "1", "--tlsrec", "1+s", "--wait-send")
            "Custom" -> parseCustomArgs(customArgs)
            "Strategy 1" -> parseCustomArgs("--tlsrec 1+s --disorder 1 --split 2 --wait-send --auto t,s")
            "Strategy 2" -> parseCustomArgs("--tlsrec 1+s --split 2 --oob 1 --wait-send --auto t,r")
            "Strategy 3" -> parseCustomArgs("--disorder 1 --split 2 --wait-send --udp-fake 5")
            "Strategy 4" -> parseCustomArgs("--tlsrec 1+s --wait-send --auto t")
            "Strategy 5" -> parseCustomArgs("--split 2 --wait-send --drop-sack")
            "Strategy 6" -> parseCustomArgs("--tlsrec 1+s --disorder 1 --split 1 --wait-send")
            "Strategy 7" -> parseCustomArgs("--oob 1 --split 2 --wait-send")
            "Strategy 8" -> parseCustomArgs("--tlsrec 1+s --split 2 --udp-fake 10 --wait-send")
            "Strategy 9" -> parseCustomArgs("--tlsrec 1+s --disorder 1 --oob 1 --split 2 --wait-send --auto t,r,s,c")
            else -> emptyArray()
        }

        if (fakeSniPool.isEmpty() || !baseArgs.contains("--fake-sni")) {
            return baseArgs
        }

        val resultList = mutableListOf<String>()
        var i = 0
        var injectedPool = false
        while (i < baseArgs.size) {
            val arg = baseArgs[i]
            if (arg == "--fake-sni") {
                if (!injectedPool) {
                    for (host in fakeSniPool) {
                        resultList.add("--fake-sni")
                        resultList.add(host)
                    }
                    injectedPool = true
                }
                if (i + 1 < baseArgs.size && !baseArgs[i + 1].startsWith("-")) {
                    i++
                }
            } else {
                resultList.add(arg)
            }
            i++
        }
        return resultList.toTypedArray()
    }

    fun validateStrategyArgs(args: Array<String>): Result<Unit> {
        if (args.isEmpty()) return Result.success(Unit)
        
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg.startsWith("-")) {
                // Basic check: if it's an option that requires a value, check if next exists and is not an option
                val needsValue = listOf(
                    "--split", "--disorder", "--oob", "--tlsrec", 
                    "--fake-sni", "--fake-tls-mod", "--udp-fake", 
                    "--await-int", "--auto", "--mod-http", "--hosts",
                    "-p", "-i", "-x", "-A", "-P"
                ).contains(arg)
                
                if (needsValue) {
                    if (i + 1 >= args.size || args[i+1].startsWith("-")) {
                        return Result.failure(IllegalArgumentException("Argument $arg requires a value"))
                    }
                    i++
                }
            }
            i++
        }
        return Result.success(Unit)
    }

    fun validateDomain(raw: String): Result<String> {
        val trimmed = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("//")
            .lowercase()

        val withoutPath = trimmed.split("/", "?", "#").first().trim()
        val domainName = if (withoutPath.contains(":") && !withoutPath.startsWith("[")) {
            withoutPath.split(":").first()
        } else {
            withoutPath
        }

        if (domainName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Domain name cannot be empty"))
        }

        if (domainName.contains(" ") || domainName.contains("\t") || domainName.contains("\n")) {
            return Result.failure(IllegalArgumentException("Domain name cannot contain spaces"))
        }

        val domainRegex = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$|^([0-9]{1,3}\\.){3}[0-9]{1,3}\$")
        if (!domainRegex.matches(domainName) && domainName != "localhost") {
            return Result.failure(IllegalArgumentException("Invalid hostname or IP address format"))
        }

        return Result.success(domainName)
    }

    private fun parseCustomArgs(args: String): Array<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < args.length) {
            when (val c = args[i]) {
                '\"' -> inQuotes = !inQuotes
                ' ' -> {
                    if (!inQuotes) {
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                            current.setLength(0)
                        }
                    } else {
                        current.append(c)
                    }
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result.toTypedArray()
    }

    val PRESETS = listOf(
        "Cloudflare" to listOf(
            "cloudflare.net", "cloudflare.com", "cloudflarecn.net", "cloudflare-ech.com"
        ),
        "Discord" to listOf(
            "dis.gd", "discord.co", "discord.gg", "discord.app", "discord.com", "discord.dev", "discord.new",
            "discord.gift", "discord.gifts", "discord.media", "discord.store", "discord.design", "discordapp.com",
            "discordcdn.com", "discordsez.com", "discordsays.com", "discordmerch.com", "discordpartygames.com",
            "discordactivities.com", "stable.dl2.discordapp.net", "discord-attachments-uploads-prd.storage.googleapis.com"
        ),
        "Torrent/Tools" to listOf(
            "rutracker.org", "nyaa.si", "rutor.org", "nnmclub.to", "speedtest.net", "ookla.com",
            "tntvillage.scambioetico.org", "piratebay.org", "thepiratebay.org", "1337x.to",
            "rarbg.to", "bt-chat.com", "torrentz.eu", "kickasstorrents.to", "extratorrent.cc",
            "torrentgalaxy.to", "yts.mx", "eztv.re", "limetorrents.pro", "zooqle.com",
            "tracker.opentrackr.org", "tracker.coppersurfer.tk", "tracker.leechers-paradise.org"
        ),
        "Socials" to listOf(
            "snapchat.com", "snap.com", "linkedin.com", "facebook.com", "fb.com", "fb.me", "fbcdn.net",
            "messenger.com", "meta.com", "instagram.com", "static.cdninstagram.com", "proton.me",
            "medium.com", "x.com", "twitter.com", "soundcloud.com"
        ),
        "Telegram" to listOf(
            "telegram.org", "core.telegram.org", "web.telegram.org", "webk.telegram.org", "my.telegram.org",
            "translations.telegram.org", "instantview.telegram.org", "blog.telegram.org", "comments.telegram.org",
            "verify.telegram.org", "login.telegram.org", "auth.telegram.org", "api.telegram.org",
            "promo.telegram.org", "desktop.telegram.org", "macos.telegram.org", "ios.telegram.org",
            "android.telegram.org", "reactions.telegram.org", "claims.telegram.org", "x.telegram.org",
            "help.telegram.org", "docs.telegram.org", "schema.telegram.org", "dev.telegram.org",
            "contest.telegram.org", "premium.telegram.org", "settings.telegram.org", "qr.telegram.org",
            "stickers.telegram.org", "emoji.telegram.org", "themes.telegram.org", "donate.telegram.org",
            "fragment.telegram.org", "ton.telegram.org", "wallet.telegram.org", "pay.telegram.org",
            "telegram.me", "telegram.dog", "telegra.ph", "telesco.pe", "web.telegram.me",
            "zws1.web.telegram.org", "zws2.web.telegram.org", "zws1.web.telegram.me", "zws2.web.telegram.me",
            "venus.web.telegram.org", "pluto.web.telegram.org", "aurora.web.telegram.org",
            "vesta.web.telegram.org", "voice.telegram.org", "cdn.telegram.org"
        ),
        "YouTube" to listOf(
            "youtu.be", "youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com",
            "googleapis.com", "googleusercontent.com", "youtubei.googleapis.com",
            "yt3.ggpht.com", "yt4.ggpht.com", "i.ytimg.com", "i9.ytimg.com",
            "nhacmp3.com.vn", "video.google.com"
        )
    )
}
