package com.cloudstream.tr.animecix

import com.fasterxml.jackson.annotation.JsonProperty

data class Category(
    @JsonProperty("pagination") val pagination: Pagination? = null,
)

data class Search(
    @JsonProperty("results") val results: List<AnimeSearch> = emptyList(),
)

data class Title(
    @JsonProperty("title") val title: Anime? = null,
)

data class Pagination(
    @JsonProperty("current_page") val currentPage: Int? = null,
    @JsonProperty("last_page") val lastPage: Int? = null,
    @JsonProperty("per_page") val perPage: Int? = null,
    @JsonProperty("data") val data: List<AnimeSearch> = emptyList(),
    @JsonProperty("total") val total: Int? = null,
)

data class AnimeSearch(
    @JsonProperty("id") val id: Int,
    @JsonProperty("title_type") val titleType: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("poster") val poster: String? = null,
)

data class Anime(
    @JsonProperty("id") val id: Int,
    @JsonProperty("title_type") val titleType: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("mal_vote_average") val rating: String? = null,
    @JsonProperty("genres") val tags: List<Genre> = emptyList(),
    @JsonProperty("trailer") val trailer: String? = null,
    @JsonProperty("credits") val actors: List<Credit> = emptyList(),
    @JsonProperty("season_count") val seasonCount: Int? = null,
    @JsonProperty("seasons") val seasons: List<Season> = emptyList(),
    @JsonProperty("videos") val videos: List<Video> = emptyList()
)

data class Genre(
    @JsonProperty("display_name") val name: String? = null,
)

data class Credit(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster") val poster: String? = null,
)

data class Video(
    @JsonProperty("episode_num") val episodeNum: Int? = null,
    @JsonProperty("season_num") val seasonNum: Int? = null,
    @JsonProperty("url") val url: String,
)

data class TitleVideos(
    @JsonProperty("videos") val videos: List<Video> = emptyList()
)

data class Season(
    @JsonProperty("number") val number: Int
)
