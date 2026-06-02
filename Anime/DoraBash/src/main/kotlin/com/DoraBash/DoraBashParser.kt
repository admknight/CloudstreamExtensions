package com.DoraBash

import com.fasterxml.jackson.annotation.JsonProperty

data class EpJson(
    val success: Boolean,
    val data: Data,
)

data class Data(
    val episodes: List<DoraBashEpisode>,
    @param:JsonProperty("max_episodes_page")
    val maxEpisodesPage: Long,
    val message: String,
)

data class DoraBashEpisode(
    val number: String,
    val thumbnail: String,
    val title: String,
    val duration: String,
    val released: String,
    @param:JsonProperty("tmdb_fetch_episode")
    val tmdbFetchEpisode: Long,
    val id: Long,
    val type: String,
    val url: String,
    @param:JsonProperty("post_title")
    val postTitle: String,
    @param:JsonProperty("meta_number")
    val metaNumber: String,
)
