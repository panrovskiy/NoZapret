package com.example.nozapret.core

object Config {
    const val DEFAULT_PROXY_HOST = "127.0.0.1"
    const val DEFAULT_PROXY_PORT = "1080"

    val BYPASS_LISTS = listOf(
        "Russia Default" to emptyArray<String>(),
        "Aggressive" to arrayOf("--drop-sack", "--mod-http", "hcsmix,dcsmix")
    )

    val STRATEGIES = listOf(
        "Auto (Recommended)" to "Adaptive multi-stage bypass (Split, Disorder, TLS Fragment, Fake SNI).",
        "Modern Ultra" to "Aggressive combination of TLS fragmentation and multi-stage desync.",
        "YouTube/Google Fix" to "Ultimate Bypass: TCP Split + TLS Fragment + QUIC Block.",
        "Discord/UDP Fix" to "Advanced UDP/QUIC desync with multi-packet fake sequences.",
        "YouTube Light (Battery)" to "Very light strategy that drains the battery less.",
        "YouTube TLS Split" to "Strategy using TLS record splitting and multi-stage disorder.",
        "RU Discord (Alt)" to "Alternative Discord bypass using fake SNI and auto-modes.",
        "Simple Split" to "Basic TCP splitting at 2nd byte.",
        "Simple Fake" to "Basic fake packet with default TTL.",
        "Custom" to "Use arguments from General settings.",
        "Torrent/P2P Fix" to "Optimized for P2P: UDP fakes and aggressive TCP splitting.",
        "Strategy 1" to "-s1 -q1 -a1 -Y -Ar -a1 -s5 -o2 -At -f-1 -r1+s -a1 -As -s1 -o1+s -s-1 -a1",
        "Strategy 2" to "-d1 -s1+s -d1+s -s3+s -d6+s -s12+s -d14+s -s20+s -d24+s -s30+s -a1",
        "Strategy 3" to "-d1 -s1+s -s3+s -s6+s -s9+s -s12+s -s15+s -s20+s -s30+s -a1",
        "Strategy 4" to "-s1 -q1 -Y -a1 -At,r,s -f-1 -r1+s -a1",
        "Strategy 5" to "-d1 -s1+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -a1"
    )

    fun getStrategyArgs(name: String, customArgs: String = ""): Array<String> {
        return when (name) {
            "Auto (Recommended)" -> arrayOf(
                "--split", "2",
                "--disorder", "1",
                "--oob", "1",
                "--fake", "-1",
                "--ttl", "5",
                "--fake-tls-mod", "rand",
                "--mod-http", "hcsmix,dcsmix",
                "--udp-fake", "3",
                "--auto", "torst,rst,tls"
            )
            "Modern Ultra" -> arrayOf(
                "--split", "1", "--disorder", "1", "--fake", "-1", "--ttl", "3", "--fake-tls-mod", "rand", "--udp-fake", "3", "--drop-sack"
            )
            "YouTube/Google Fix" -> arrayOf(
                "--split", "1", "--disorder", "1", "--oob", "1", "--fake", "-1", "--ttl", "3", "--fake-tls-mod", "rand", "--mod-http", "hcsmix,dcsmix", "--udp-fake", "3", "--drop-sack"
            )
            "Discord/UDP Fix" -> arrayOf(
                "--split", "1", "--disorder", "1", "--udp-fake", "5", "--ttl", "3", "--fake", "-1", "--drop-sack"
            )
            "YouTube Light (Battery)" -> parseCustomArgs("-H:\"signaler-pa.youtube.com\" -o 1 -r 5+s -r 16+s -An -H:\"youtube.com\" -o 1 -r -8+se -r -4+se -An -H:\"youtu.be\" -o 1 -r -6+se -r -3+se -An -H:\"googlevideo.com\" -o 1 -r -11+se -r -5+se -An -H:\"ytimg.com ggpht.com youtubei.googleapis.com yt3.googleusercontent.com\" -o 1 -r 1+s -An")
            "YouTube TLS Split" -> parseCustomArgs("-H:\"youtube.com youtu.be ytimg.com ggpht.com googleapis.com googleusercontent.com signaler-pa.youtube.com\" --tlsrec 4+s --tlsrec 8+s --tlsrec 12+s --tlsrec 16+s --tlsrec 20+s --disorder 25+s -H:\"googlevideo.com\" --tlsrec 14+s --disorder 25+s")
            "RU Discord (Alt)" -> arrayOf("--disorder", "1:2", "--fake", "-1", "--ttl", "4", "--fake-sni", "yandex.ru", "--udp-fake", "3")
            "Simple Split" -> arrayOf("--split", "2")
            "Simple Fake" -> arrayOf("--fake", "-1", "--fake-sni", "yandex.ru", "--ttl", "4")
            "Torrent/P2P Fix" -> arrayOf("--split", "1", "--udp-fake", "5", "--auto", "none", "--drop-sack")
            "Custom" -> parseCustomArgs(customArgs)
            "Strategy 1" -> parseCustomArgs("-s1 -q1 -a1 -Y -Ar -a1 -s5 -o2 -At -f-1 -r1+s -a1 -As -s1 -o1+s -s-1 -a1")
            "Strategy 2" -> parseCustomArgs("-d1 -s1+s -d1+s -s3+s -d6+s -s12+s -d14+s -s20+s -d24+s -s30+s -a1")
            "Strategy 3" -> parseCustomArgs("-d1 -s1+s -s3+s -s6+s -s9+s -s12+s -s15+s -s20+s -s30+s -a1")
            "Strategy 4" -> parseCustomArgs("-s1 -q1 -Y -a1 -At,r,s -f-1 -r1+s -a1")
            "Strategy 5" -> parseCustomArgs("-d1 -s1+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -a1")
            else -> emptyArray()
        }
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
