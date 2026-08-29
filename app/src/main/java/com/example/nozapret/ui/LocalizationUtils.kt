package com.example.nozapret.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.nozapret.R
import compose.icons.SimpleIcons
import compose.icons.simpleicons.*

@Composable
fun getLocalizedStrategyName(name: String): String {
    return when (name) {
        "Auto (Recommended)" -> stringResource(R.string.strategy_auto)
        "Modern Ultra" -> stringResource(R.string.strategy_modern_ultra)
        "YouTube/Google Fix" -> stringResource(R.string.strategy_youtube_google_fix)
        "Discord/UDP Fix" -> stringResource(R.string.strategy_discord_udp_fix)
        "RU YouTube (Alt)" -> stringResource(R.string.strategy_ru_youtube_alt)
        "YouTube Fix" -> stringResource(R.string.strategy_youtube_fix)
        "Discord Fix" -> stringResource(R.string.strategy_discord_fix)
        "RU Discord (Alt)" -> stringResource(R.string.strategy_ru_discord_alt)
        "Disorder & Fake" -> stringResource(R.string.strategy_disorder_fake)
        "TLS Record Split" -> stringResource(R.string.strategy_tls_split)
        "OOB & Split" -> stringResource(R.string.strategy_oob_split)
        "Modern Mixed" -> stringResource(R.string.strategy_modern_mixed)
        "Aggressive Mixed" -> stringResource(R.string.strategy_aggressive_mixed)
        "Advanced Fake" -> stringResource(R.string.strategy_advanced_fake)
        "Advanced TLS" -> stringResource(R.string.strategy_advanced_tls)
        "HTTP Desync" -> stringResource(R.string.strategy_http_desync)
        "UDP/QUIC Fix" -> stringResource(R.string.strategy_udp_quic_fix)
        "SNI Spoofing" -> stringResource(R.string.strategy_sni_spoofing)
        "TLS Minor Version" -> stringResource(R.string.strategy_tls_minor)
        "Fake TLS Mod" -> stringResource(R.string.strategy_fake_tls_mod)
        "Experimental (Multi-Group)" -> stringResource(R.string.strategy_experimental)
        "RU YouTube Alt (New)" -> stringResource(R.string.strategy_ru_youtube_alt_new)
        "RU Discord Alt (New)" -> stringResource(R.string.strategy_ru_discord_alt_new)
        "Kyber-Aware" -> stringResource(R.string.strategy_kyber_aware)
        "Extreme RU" -> stringResource(R.string.strategy_extreme_ru)
        "Torrent/P2P Fix" -> stringResource(R.string.strategy_torrent_fix)
        "Strategy 1" -> stringResource(R.string.strategy_1)
        "Strategy 2" -> stringResource(R.string.strategy_2)
        "Strategy 3" -> stringResource(R.string.strategy_3)
        "Strategy 4" -> stringResource(R.string.strategy_4)
        "Strategy 5" -> stringResource(R.string.strategy_5)
        "Simple Split" -> stringResource(R.string.strategy_simple_split)
        "Simple Fake" -> stringResource(R.string.strategy_simple_fake)
        "OOB Data" -> stringResource(R.string.strategy_oob_data)
        "Host Mix" -> stringResource(R.string.strategy_host_mix)
        "TLS Record fragmentation" -> stringResource(R.string.strategy_tls_record_frag)
        "Custom" -> stringResource(R.string.strategy_custom)
        else -> name
    }
}

@Composable
fun getLocalizedStrategyDesc(name: String, fallback: String): String {
    return when (name) {
        "Auto (Recommended)" -> stringResource(R.string.strategy_auto_desc)
        "Modern Ultra" -> stringResource(R.string.strategy_modern_ultra_desc)
        "YouTube/Google Fix" -> stringResource(R.string.strategy_youtube_google_fix_desc)
        "Discord/UDP Fix" -> stringResource(R.string.strategy_discord_udp_fix_desc)
        "YouTube Fix" -> stringResource(R.string.strategy_youtube_fix_desc)
        "RU YouTube (Alt)" -> stringResource(R.string.strategy_ru_youtube_alt_desc)
        "Discord Fix" -> stringResource(R.string.strategy_discord_fix_desc)
        "RU Discord (Alt)" -> stringResource(R.string.strategy_ru_discord_alt_desc)
        "Disorder & Fake" -> stringResource(R.string.strategy_disorder_fake_desc)
        "TLS Record Split" -> stringResource(R.string.strategy_tls_split_desc)
        "OOB & Split" -> stringResource(R.string.strategy_oob_split_desc)
        "Modern Mixed" -> stringResource(R.string.strategy_modern_mixed_desc)
        "Aggressive Mixed" -> stringResource(R.string.strategy_aggressive_mixed_desc)
        "Advanced Fake" -> stringResource(R.string.strategy_advanced_fake_desc)
        "Advanced TLS" -> stringResource(R.string.strategy_advanced_tls_desc)
        "HTTP Desync" -> stringResource(R.string.strategy_http_desync_desc)
        "UDP/QUIC Fix" -> stringResource(R.string.strategy_udp_quic_fix_desc)
        "SNI Spoofing" -> stringResource(R.string.strategy_sni_spoofing_desc)
        "TLS Minor Version" -> stringResource(R.string.strategy_tls_minor_desc)
        "Fake TLS Mod" -> stringResource(R.string.strategy_fake_tls_mod_desc)
        "Experimental (Multi-Group)" -> stringResource(R.string.strategy_experimental_desc)
        "RU YouTube Alt (New)" -> stringResource(R.string.strategy_ru_youtube_alt_new_desc)
        "RU Discord Alt (New)" -> stringResource(R.string.strategy_ru_discord_alt_new_desc)
        "Kyber-Aware" -> stringResource(R.string.strategy_kyber_aware_desc)
        "Extreme RU" -> stringResource(R.string.strategy_extreme_ru_desc)
        "Torrent/P2P Fix" -> stringResource(R.string.strategy_torrent_fix_desc)
        "Strategy 1" -> stringResource(R.string.strategy_1)
        "Strategy 2" -> stringResource(R.string.strategy_2)
        "Strategy 3" -> stringResource(R.string.strategy_3)
        "Strategy 4" -> stringResource(R.string.strategy_4)
        "Strategy 5" -> stringResource(R.string.strategy_5)
        "Simple Split" -> stringResource(R.string.strategy_simple_split_desc)
        "Simple Fake" -> stringResource(R.string.strategy_simple_fake_desc)
        "OOB Data" -> stringResource(R.string.strategy_oob_data_desc)
        "Host Mix" -> stringResource(R.string.strategy_host_mix_desc)
        "TLS Record fragmentation" -> stringResource(R.string.strategy_tls_record_frag_desc)
        "Custom" -> stringResource(R.string.strategy_custom_desc)
        else -> fallback
    }
}


@Composable
fun getLocalizedPresetName(name: String): String {
    return when (name) {
        "YouTube" -> stringResource(R.string.preset_youtube)
        "Telegram" -> stringResource(R.string.preset_telegram)
        "Discord" -> stringResource(R.string.preset_discord)
        "WhatsApp" -> stringResource(R.string.preset_whatsapp)
        "Character AI" -> stringResource(R.string.preset_character_ai)
        "Cloudflare" -> stringResource(R.string.preset_cloudflare)
        "Google" -> stringResource(R.string.preset_google)
        "Steam" -> stringResource(R.string.preset_steam)
        "Socials" -> stringResource(R.string.preset_socials)
        "Media" -> stringResource(R.string.preset_media)
        "Custom" -> stringResource(R.string.preset_custom)
        else -> name
    }
}

fun getPresetIcon(name: String): ImageVector {
    return when (name) {
        "YouTube" -> SimpleIcons.Youtube
        "Telegram" -> SimpleIcons.Telegram
        "Discord" -> SimpleIcons.Discord
        "WhatsApp" -> SimpleIcons.Whatsapp
        "Character AI" -> SimpleIcons.Openai
        "Cloudflare" -> SimpleIcons.Cloudflare
        "Google" -> SimpleIcons.Google
        "Steam" -> SimpleIcons.Steam
        "Socials" -> Icons.Rounded.Public
        "Media" -> Icons.Rounded.Movie
        "Custom" -> Icons.Rounded.Edit
        else -> Icons.Rounded.Extension
    }
}

fun parseSimpleHtml(html: String): AnnotatedString {
    val bulletedHtml = html.replace("•", "  •  ")
    return buildAnnotatedString {
        var lastIndex = 0
        val regex = Regex("<b>|</b>|<i>|</i>|<hr/>")
        val matches = regex.findAll(bulletedHtml)
        
        val styleStack = mutableListOf<SpanStyle>()
        
        for (match in matches) {
            val preText = bulletedHtml.substring(lastIndex, match.range.first)
            if (styleStack.isEmpty()) {
                append(preText)
            } else {
                var combinedStyle = SpanStyle()
                styleStack.forEach { combinedStyle = combinedStyle.merge(it) }
                withStyle(combinedStyle) {
                    append(preText)
                }
            }
            
            when (match.value) {
                "<b>" -> styleStack.add(SpanStyle(fontWeight = FontWeight.Bold))
                "</b>" -> if (styleStack.isNotEmpty()) styleStack.removeLastOrNull()
                "<i>" -> styleStack.add(SpanStyle(fontStyle = FontStyle.Italic))
                "</i>" -> if (styleStack.isNotEmpty()) styleStack.removeLastOrNull()
                "<hr/>" -> {
                    // Handled by split in UI
                }
            }
            lastIndex = match.range.last + 1
        }
        
        val remainingText = bulletedHtml.substring(lastIndex)
        if (styleStack.isEmpty()) {
            append(remainingText)
        } else {
            var combinedStyle = SpanStyle()
            styleStack.forEach { combinedStyle = combinedStyle.merge(it) }
            withStyle(combinedStyle) {
                append(remainingText)
            }
        }
    }
}
