import pytest
from tools.scraping.detection import (
    is_cloudflare_challenge,
    is_bot_blocked,
    verify_redirect_safety,
    verify_content_markers,
    is_soft_404
)

def test_is_cloudflare_challenge():
    cf_body = "<html><title>Just a moment...</title><body><div id='challenge-running'>Checking your browser...</div></body></html>"
    assert is_cloudflare_challenge(403, cf_body) is True
    assert is_cloudflare_challenge(200, cf_body) is True
    assert is_cloudflare_challenge(200, "<html><head><title>Welcome</title></head></html>") is False

def test_is_bot_blocked():
    assert is_bot_blocked(403, "Access Denied") is True
    assert is_bot_blocked(429, "Too Many Requests") is True
    assert is_bot_blocked(200, "Normal Page") is False

def test_verify_redirect_safety_exact_and_subdomain():
    allowed = {"example.com", "www.example.com"}
    
    # Exact matches pass
    safe, cand = verify_redirect_safety("https://example.com/home", allowed)
    assert safe is True
    assert cand is None

    safe, cand = verify_redirect_safety("https://www.example.com/home", allowed)
    assert safe is True

    # Subdomain rejected by default (allow_subdomains=False)
    safe, cand = verify_redirect_safety("https://sub.example.com/home", allowed, allow_subdomains=False)
    assert safe is False
    assert cand == "sub.example.com"

    # Subdomain allowed when opt-in (allow_subdomains=True)
    safe, cand = verify_redirect_safety("https://sub.example.com/home", allowed, allow_subdomains=True)
    assert safe is True

    # Malicious redirect rejected
    safe, cand = verify_redirect_safety("https://malicious.com/login", allowed)
    assert safe is False
    assert cand == "malicious.com"

def test_verify_redirect_empty_allowlist_fails_closed():
    # In health mode, empty allowlist is a CONFIG_EMPTY_ALLOWLIST failure
    safe, cand = verify_redirect_safety("https://example.com/home", set(), is_probe_mode=False)
    assert safe is False
    assert cand == "CONFIG_EMPTY_ALLOWLIST"

    # In probe mode, empty allowlist is permissive for exploration
    safe, cand = verify_redirect_safety("https://example.com/home", set(), is_probe_mode=True)
    assert safe is True

def test_verify_content_markers():
    body = "<html><body><h1>Yeşilçam Klasikleri</h1></body></html>"
    assert verify_content_markers(body, ["Yeşilçam", "yesilcam"]) is True
    assert verify_content_markers(body, ["AnimeciX", "anime"]) is False

def test_is_soft_404_detection():
    assert is_soft_404(404, "Page Not Found") is True
    assert is_soft_404(200, "<html><title>404 — Sayfa Bulunamadı | Belgeselx</title></html>", "404 — Sayfa Bulunamadı | Belgeselx") is True
    assert is_soft_404(200, "<html><title>Page not found</title></html>", "Page not found") is True
    assert is_soft_404(200, "<html><body><h1>Aradığınız içerik bulunamadı</h1></body></html>") is True
    assert is_soft_404(200, "<html><title>Gezegenimiz Belgeseli İzle</title><body>Normal içerik</body></html>", "Gezegenimiz Belgeseli İzle") is False
