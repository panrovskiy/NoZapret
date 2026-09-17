package com.example.nozapret.core

object Config {
    const val DEFAULT_PROXY_HOST = "127.0.0.1"
    const val DEFAULT_PROXY_PORT = "1080"

    val BYPASS_LISTS = listOf(
        "Russia Default" to emptyArray<String>(),
        "Aggressive" to arrayOf("--drop-sack", "--mod-http", "h,d"),
    )

    val STRATEGIES = listOf(
        "Auto (Recommended)" to "Adaptive multi-stage bypass (Split, Disorder, TLS Fragment, Fake SNI).",
        "Modern Ultra" to "Aggressive combination of TLS fragmentation and multi-stage desync.",
        "YouTube/Google Fix" to "Ultimate Bypass: TCP Split + TLS Fragment + QUIC Block.",
        "Discord/UDP Fix" to "Advanced UDP/QUIC desync with multi-packet fake sequences.",
        "YouTube Light (Battery)" to "Very light strategy that drains the battery less.",
        "YouTube TLS Split" to "Strategy using TLS record splitting and multi-stage disorder.",
        "RU Discord (Alt)" to "Alternative Discord bypass using fake SNI and auto-modes.",
        "RU Universal (Powerful)" to "Aggressive bypass for most blocked sites in Russia.",
        "Simple Split" to "Basic TCP splitting at 2nd byte.",
        "Simple Fake" to "Basic fake packet with default TTL.",
        "Custom" to "Use arguments from General settings.",
        "Torrent/P2P Fix" to "Optimized for P2P: UDP fakes and aggressive TCP splitting.",
        "RU Discord (Alt 2)" to "Advanced Discord strategy with randomized UDP fakes and OOB splitting.",
        "Modern YouTube (Extreme)" to "Aggressive YouTube bypass using multiple desync techniques.",
        "Universal Web (Safe)" to "Safe general bypass strategy for standard web browsing.",
        "Strategy 1" to "-s1 -q1 -a1 -Y -Ar -a1 -s5 -o2 -At -f-1 -r1+s -a1 -As -s1 -o1+s -s-1 -a1",
        "Strategy 2" to "-d1 -s1+s -d1+s -s3+s -d6+s -s12+s -d14+s -s20+s -d24+s -s30+s -a1",
        "Strategy 3" to "-d1 -s1+s -s3+s -s6+s -s9+s -s12+s -s15+s -s20+s -s30+s -a1",
        "Strategy 4" to "-s1 -q1 -Y -a1 -At,r,s -f-1 -r1+s -a1",
        "Strategy 5" to "-d1 -s1+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -a1",
        "Strategy 6" to "-d1 -s1+s -d2+s -s4+s -d8+s -s16+s -d32+s -s64+s -a1",
        "Strategy 7" to "-o1 -q1 -s1 -d1 -f-1 -r1+s -a1",
        "Strategy 8" to "-d1 -s1+s -d1+s -s2+s -d2+s -s3+s -d3+s -s4+s -d4+s -a1",
        "Strategy 9" to "-s1 -o1 -q1 -Y -Ar -At -As -f-1 -r1+s -a1"
    )

    fun getStrategyArgs(name: String, customArgs: String = ""): Array<String> {
        return when (name) {
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
                "--udp-fake", "20",
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
            "RU Discord (Alt)" -> arrayOf("--disorder", "1", "--split", "1", "--wait-send", "--udp-fake", "10")
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
