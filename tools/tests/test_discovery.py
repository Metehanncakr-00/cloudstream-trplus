import pytest
from tools.scraping.discovery import (
    discover_homepage_cards,
    parse_detail_page,
    evaluate_player_discovery,
    classify_subtitles
)


@pytest.mark.parametrize("missing", [None, "container", "sdk", "reference"])
def test_dyg_player_requires_container_sdk_and_reference(missing):
    parts = {
        "container": '<div id="dyg-player"></div>',
        "sdk": '<link rel="preload" as="script" href="https://player-assets.dygdigital.com/prod/js/bundle.js">',
        "reference": r'<script>self.__next_f.push([1,"{\"referenceId\":\"6a97c234a46e38b3a3925061\"}"])</script>',
    }
    html = "".join(value for key, value in parts.items() if key != missing)

    status, info = evaluate_player_discovery(html, "https://www.startv.com.tr/dizi/test/bolumler/1-bolum")

    assert status == ("PLAYER_DISCOVERED" if missing is None else "PLAYER_NOT_FOUND")
    assert info["hasPlayerScript"] is (missing is None)
    assert info["mediaUrls"] == []

def test_discover_homepage_cards_strict():
    html_with_cards = """
    <div class="movies">
        <a class="poster" href="/film/movie-1" title="Movie One"><img src="/img1.jpg"/></a>
        <a class="poster" href="/film/movie-2" title="Movie Two"><img src="/img2.jpg"/></a>
    </div>
    """
    cards = discover_homepage_cards(html_with_cards, "https://moviesite.test")
    assert len(cards) == 2
    assert cards[0]["title"] == "Movie One"
    assert cards[0]["url"] == "https://moviesite.test/film/movie-1"

    empty_html = "<div><p>No movies available</p></div>"
    assert len(discover_homepage_cards(empty_html, "https://moviesite.test")) == 0

def test_parse_detail_page_and_soft_404():
    soft_404_html = "<html><title>404 — Sayfa Bulunamadı | Belgeselx</title><body>Böyle bir sayfa yok</body></html>"
    res_404 = parse_detail_page(soft_404_html, 200, "https://belgeselx.com/belgesel/non-existent")
    assert res_404["status"] == "FAIL"
    assert res_404["isSoft404"] is True
    assert res_404["reason"] == "SOFT_404_PAGE"

    valid_html = """
    <html>
        <h1>Silo 1. Sezon</h1>
        <div class="episodes">
            <a href="/dizi/silo/1-sezon-1-bolum">1. Bölüm</a>
            <a href="/dizi/silo/1-sezon-2-bolum">2. Bölüm</a>
        </div>
    </html>
    """
    res_valid = parse_detail_page(valid_html, 200, "https://dizipal.test/dizi/silo")
    assert res_valid["status"] == "PASS"
    assert res_valid["title"] == "Silo 1. Sezon"
    assert len(res_valid["episodeLinks"]) == 2
    assert res_valid["episodeLinks"][0] == "https://dizipal.test/dizi/silo/1-sezon-1-bolum"

def test_evaluate_player_discovery_avoids_generic_substring():
    # Mere presence of word 'player' should NOT trigger discovery
    fake_html = "<html><body><div class='player-stats-section'><h3>Top Players</h3><p>Cristiano Ronaldo</p></div></body></html>"
    stat, info = evaluate_player_discovery(fake_html, "https://sports.test")
    assert stat == "PLAYER_NOT_FOUND"

    # Real iframe embed or video player script triggers discovery
    real_iframe_html = "<html><body><iframe src='https://vidpapi.xyz/video/12345' width='640'></iframe></body></html>"
    stat, info = evaluate_player_discovery(real_iframe_html, "https://stream.test")
    assert stat == "PLAYER_DISCOVERED"
    assert len(info["iframes"]) == 1

def test_classify_subtitles_truthful():
    assert classify_subtitles("<html><track src='sub.vtt' kind='subtitles'></html>") == "VTT/SRT"
    assert classify_subtitles("<html><h1>Movie Title (Türkçe Dublaj)</h1></html>", "Movie Title (Türkçe Dublaj)") == "DUBBED"
    assert classify_subtitles("<html><h1>Movie Title (Türkçe Altyazılı)</h1></html>", "Movie Title (Türkçe Altyazılı)") == "HARDSUB"
    assert classify_subtitles("<html><h1>Regular Movie</h1></html>", "Regular Movie") == "NONE"

def test_turkanime_navigation_is_not_content():
    """Verifies that navigation links like .#0-9 or letter filters are NEVER parsed as content items."""
    html = """
    <div class="letter-nav">
        <a class="animelist-item" href="/anime-harf/#">.#0-9</a>
        <a class="animelist-item" href="/anime-harf/A">A</a>
        <a class="animelist-item" href="/anime-harf/B">B</a>
    </div>
    <div class="panel">
        <div class="panel-title">
            <a href="/anime/death-note">Death Note</a>
        </div>
        <img src="/death_note.jpg"/>
    </div>
    """
    homepage_cfg = {
        "selectors": ["div.panel-title a", "a.animelist-item"],
        "requiredUrlPatterns": ["/anime/"],
        "excludedUrlPatterns": ["harf/", "harf-", "#"]
    }
    cards = discover_homepage_cards(html, "https://www.turkanime.tv/", homepage_cfg=homepage_cfg)
    assert len(cards) == 1
    assert cards[0]["title"] == "Death Note"
    assert cards[0]["url"] == "https://www.turkanime.tv/anime/death-note"
    assert not any(c["title"] in [".#0-9", "A", "B"] for c in cards)

def test_yesilcamtv_category_is_not_content():
    """Verifies that category/genre links like 'Belgesel' are NEVER parsed as movie content items."""
    html = """
    <div class="category-menu">
        <a href="/kategori/belgesel/">Belgesel</a>
        <a href="/kategori/komedi/">Komedi</a>
    </div>
    <div class="movies-list">
        <article>
            <a href="/sahte-kabadayi-izle/" title="Sahte Kabadayı"><img src="/poster.jpg"/></a>
        </article>
    </div>
    """
    homepage_cfg = {
        "selectors": [".movies-list a", "article a", ".category-menu a"],
        "requiredUrlPatterns": ["-izle"],
        "excludedUrlPatterns": ["/kategori/", "/category/", "/genre/"]
    }
    cards = discover_homepage_cards(html, "https://yesilcamtv.com.tr/", homepage_cfg=homepage_cfg)
    assert len(cards) == 1
    assert cards[0]["title"] == "Sahte Kabadayı"
    assert cards[0]["url"] == "https://yesilcamtv.com.tr/sahte-kabadayi-izle/"
    assert not any(c["title"].lower() in ["belgesel", "komedi"] for c in cards)

def test_episode_selector_strict_config_vs_fallback():
    """Verifies STRICT_CONFIG vs GENERIC_FALLBACK and non-episode exclusion."""
    html = """
    <html>
        <h1>Death Note</h1>
        <div class="menu">
            <a href="/yakinda-yeni-sezon-animeleri">Yakında Yeni Sezon</a>
        </div>
        <div class="episodes">
            <a class="custom-ep" href="/video/death-note-1-bolum">1. Bölüm</a>
        </div>
    </html>
    """
    # 1. When configured selector is passed -> STRICT_CONFIG
    res_strict = parse_detail_page(
        html,
        200,
        "https://www.turkanime.tv/anime/death-note",
        episode_selector="a.custom-ep"
    )
    assert res_strict["episodeDiscoveryMode"] == "STRICT_CONFIG"
    assert len(res_strict["episodeLinks"]) == 1
    assert res_strict["episodeLinks"][0] == "https://www.turkanime.tv/video/death-note-1-bolum"

    # 2. When configured selector is None or empty, fallback kicks in but excludes 'yakinda-yeni-sezon-animeleri'
    res_fallback = parse_detail_page(
        html,
        200,
        "https://www.turkanime.tv/anime/death-note",
        episode_selector=None
    )
    assert res_fallback["episodeDiscoveryMode"] == "GENERIC_FALLBACK"
    assert len(res_fallback["episodeLinks"]) == 1
    assert res_fallback["episodeLinks"][0] == "https://www.turkanime.tv/video/death-note-1-bolum"
    assert not any("yakinda" in link for link in res_fallback["episodeLinks"])

def test_evaluate_player_discovery_scx_source():
    # Empty/malformed SCX
    empty_scx_html = "<html><body><script>var scx = {};</script></body></html>"
    stat, info = evaluate_player_discovery(empty_scx_html, "https://movies.test")
    assert stat == "PLAYER_NOT_FOUND"
    assert not info["hasPlayerScript"]

    # Valid decodable SCX source
    import base64
    import codecs
    target = "https://vidpapi.com/embed/123"
    b64 = base64.b64encode(target.encode("utf-8")).decode("utf-8")
    rtt = codecs.encode(b64.rstrip("="), 'rot_13')

    valid_scx_html = f'<html><body><script>var scx = {{"token": "{rtt}"}};</script></body></html>'
    stat, info = evaluate_player_discovery(valid_scx_html, "https://movies.test")
    assert stat == "PLAYER_DISCOVERED"
    assert info["hasPlayerScript"]
    assert "https://vidpapi.com/embed/123" in info["iframes"]

def test_evaluate_player_discovery_scx_false_positive():
    # If SCX contains a player name like 'rapidrame' but does not decode to a valid URL, it should be ignored.
    false_positive_html = '<html><body><script>var scx = {"some_key": "rapidrame_token"};</script></body></html>'
    stat, info = evaluate_player_discovery(false_positive_html, "https://movies.test")
    assert stat == "PLAYER_NOT_FOUND"
    assert not info["hasPlayerScript"]

