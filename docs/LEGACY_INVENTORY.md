# Legacy Plugin Inventory & Migration Matrix

Bu dokuman, eski `Kraptor123/cs-kraptor` repository'sinden cikarilan **67 adet** eklentinin dokumunu ve guncel durum analizini icerir.

## Ozet Istatistikler

- **Toplam Legacy Eklenti:** 67
- **:** 67

---

## Eklenti Listesi

| # | Eklenti Adi | Internal Name | Legacy Surum | Legacy Status | Dil | Turler | Bilinen Domain | Migration Durumu | Notlar |
|---|-------------|---------------|--------------|---------------|-----|--------|----------------|------------------|--------|
| 1 | **AnimeciX** | `AnimeciX` | 88 | 1 | tr | Anime | https://animecix.tv | `implemented` | Batch 01 revived: Native REST API + TauVideo extractor |
| 2 | **Animeler** | `Animeler` | 41 | 1 | tr | Anime | https://animeler.pw | `eligible` | Public domain identified, eligible for migration |
| 3 | **Animely** | `Animely` | 4 | 1 | tr | Anime, AnimeMovie, OVA | https://animecim.tv | `eligible` | Public domain identified, eligible for migration |
| 4 | **AnimPow** | `AnimPow` | 7 | 1 | tr | Anime | https://animpow.com | `eligible` | Public domain identified, eligible for migration |
| 5 | **Anizium** | `Anizium` | 25 | 1 | tr | AnimeMovie, Anime | https://anizium.co | `eligible` | Public domain identified, eligible for migration |
| 6 | **AsyaAnimeleri** | `AsyaAnimeleri` | 24 | 1 | tr | Anime | https://asyaanimeleri.top | `eligible` | Public domain identified, eligible for migration |
| 7 | **AsyaFanatiklerim** | `AsyaFanatiklerim` | 22 | 1 | tr | AsianDrama | https://asyafanatiklerim.com | `eligible` | Public domain identified, eligible for migration |
| 8 | **AsyaMinik** | `AsyaMinik` | 17 | 0 | tr | AsianDrama | https://asyaminik.com | `deprecated` | Marked status=0 (Down) in legacy builds repository |
| 9 | **AsyaWatch** | `AsyaWatch` | 41 | 1 | tr | AsianDrama | https://asyawatch.com | `eligible` | Public domain identified, eligible for migration |
| 10 | **BelgeselX** | `BelgeselX` | 41 | 1 | tr | Documentary | https://belgeselx.com | `implemented` | Batch 01 revived: Google CSE search + multi-source player |
| 11 | **CizgiMax** | `CizgiMax` | 36 | 1 | tr | Cartoon | https://cizgimax.online | `eligible` | Public domain identified, eligible for migration |
| 12 | **CizgiveDizi** | `CizgiveDizi` | 50 | 1 | tr | Cartoon | https://cizgivedizi.com | `eligible` | Public domain identified, eligible for migration |
| 13 | **Ddizi** | `Ddizi` | 19 | 1 | tr | TvSeries | https://www.ddizi.im | `eligible` | Public domain identified, eligible for migration |
| 14 | **DiziAsia** | `DiziAsia` | 20 | 1 | tr | AsianDrama | https://diziasia.com | `eligible` | Public domain identified, eligible for migration |
| 15 | **DiziAsya** | `DiziAsya` | 27 | 1 | tr | AsianDrama | https://api.diziasya.com | `eligible` | Public domain identified, eligible for migration |
| 16 | **DiziBox** | `DiziBox` | 37 | 1 | tr | TvSeries | https://www.dizibox.live | `eligible` | Public domain identified, eligible for migration |
| 17 | **DiziFilmORG** | `DiziFilmORG` | 22 | 1 | tr | Movie, TvSeries | https://dizifilmizle.to | `eligible` | Public domain identified, eligible for migration |
| 18 | **Dizigecesi** | `Dizigecesi` | 4 | 1 | tr | Movie, TvSeries | https://dizigecesi.com | `eligible` | Public domain identified, eligible for migration |
| 19 | **DiziKorea** | `DiziKorea` | 35 | 1 | tr | AsianDrama | https://dizikorea3.com | `eligible` | Public domain identified, eligible for migration |
| 20 | **DiziLife** | `DiziLife` | 58 | 1 | tr | Movie, TvSeries | https://dizi74.life | `eligible` | Public domain identified, eligible for migration |
| 21 | **Dizilla** | `Dizilla` | 111 | 1 | tr | TvSeries | https://dizilla.now | `eligible` | Public domain identified, eligible for migration |
| 22 | **DiziMom** | `DiziMom` | 61 | 1 | tr | TvSeries | https://www.dizimom.diy | `eligible` | Public domain identified, eligible for migration |
| 23 | **DiziPal** | `DiziPal` | 89 | 1 | tr | Movie, TvSeries | https://dizipal1430.com | `implemented` | Batch 01 revived: Search API + Videoplay HLS stream extractor |
| 24 | **DiziPalOrijinal** | `DiziPalOrijinal` | 45 | 1 | tr | TvSeries | https://dizipal1580.com | `eligible` | Public domain identified, eligible for migration |
| 25 | **Dizipod** | `Dizipod` | 12 | 1 | tr | Movie, TvSeries, AsianDrama | https://dizipod.com | `eligible` | Public domain identified, eligible for migration |
| 26 | **DiziYo** | `DiziYo` | 74 | 1 | tr | TvSeries | https://www.diziyo.so | `eligible` | Public domain identified, eligible for migration |
| 27 | **DiziYou** | `DiziYou` | 49 | 1 | tr | TvSeries | https://www.diziyou.one | `eligible` | Public domain identified, eligible for migration |
| 28 | **DramaDizilerim** | `DramaDizilerim` | 3 | 1 | tr | AsianDrama | https://dramadizilerim.com | `eligible` | Public domain identified, eligible for migration |
| 29 | **FilmEkseni** | `FilmEkseni` | 50 | 1 | tr | Movie | https://filmekseni.vip | `eligible` | Public domain identified, eligible for migration |
| 30 | **FilmHane** | `FilmHane` | 43 | 1 | tr | Movie | https://www.filmhane.shop | `eligible` | Public domain identified, eligible for migration |
| 31 | **FilmMakinesi** | `FilmMakinesi` | 70 | 1 | tr | Movie, TvSeries | https://filmmakinesi.to | `implemented` | Batch 01 revived: Movie/Series parser + CloseLoad dynamic extractor |
| 32 | **FilmModu** | `FilmModu` | 41 | 1 | tr | Movie | https://www.filmmodu.one | `eligible` | Public domain identified, eligible for migration |
| 33 | **Filmzal** | `Filmzal` | 16 | 1 | az | Movie | https://filmzal.me | `eligible` | Public domain identified, eligible for migration |
| 34 | **FullHDFilmizlesene** | `FullHDFilmizlesene` | 72 | 1 | tr | Movie | https://www.fullhdfilmizlesene.now | `eligible` | Public domain identified, eligible for migration |
| 35 | **GinikoCanli** | `GinikoCanli` | 15 | 1 | tr | Live | https://www.giniko.com | `eligible` | Public domain identified, eligible for migration |
| 36 | **HDFilmCehennemi** | `HDFilmCehennemi` | 97 | 1 | tr | Movie, TvSeries | https://www.hdfilmcehennemi.nl | `implemented` | Batch 01 revived: Fetch search + Rapidrame dynamic extractor |
| 37 | **HDFilmDelisi** | `HDFilmDelisi` | 18 | 1 | tr | Movie | https://hdfilmdelisi.one | `eligible` | Public domain identified, eligible for migration |
| 38 | **HDFilmizle** | `HDFilmizle` | 25 | 1 | tr | Movie | https://www.hdfilmizle.vip | `eligible` | Public domain identified, eligible for migration |
| 39 | **InatBox** | `InatBox` | 57 | 2 | tr | Movie, TvSeries, Live | - | `blocked` | IPTV private backend / subscription bypass / reverse engineered token |
| 40 | **JetFilmizle** | `JetFilmizle` | 62 | 1 | tr | Movie | https://jetfilmizle.now | `eligible` | Public domain identified, eligible for migration |
| 41 | **KickTR** | `KickTR` | 17 | 1 | tr | Live | https://kick.com | `eligible` | Public domain identified, eligible for migration |
| 42 | **KraptorPlus** | `KraptorPlus` | 86 | 1 | tr | TvSeries, Movie, Anime, Cartoon, AsianDrama, AnimeMovie | - | `blocked` | Meta-aggregator with mixed unknown upstream endpoints |
| 43 | **KultFilmler** | `KultFilmler` | 31 | 1 | tr | Movie, TvSeries | https://kultfilmler.net | `eligible` | Public domain identified, eligible for migration |
| 44 | **MirrorVerse** | `MirrorVerse` | 38 | 1 | tr | Movie, TvSeries | https://mirrorverse.online | `eligible` | Public domain identified, eligible for migration |
| 45 | **OnePaceTr** | `OnePaceTr` | 13 | 1 | tr | Anime | https://onepacetr.net | `eligible` | Public domain identified, eligible for migration |
| 46 | **OpenAnime** | `OpenAnime` | 28 | 1 | tr | Anime, AnimeMovie | https://openani.me | `eligible` | Public domain identified, eligible for migration |
| 47 | **RareFilmm** | `RareFilmm` | 20 | 1 | en | Movie | https://rarefilmm.com | `eligible` | Public domain identified, eligible for migration |
| 48 | **RecTV** | `RecTV` | 43 | 1 | tr | Movie, Live, TvSeries | - | `blocked` | IPTV private backend / encrypted session tokens |
| 49 | **SeiCode** | `SeiCode` | 25 | 1 | tr | Anime | https://seiwatch.net | `eligible` | Public domain identified, eligible for migration |
| 50 | **SelcukFlix** | `SelcukFlix` | 49 | 1 | tr | Movie, TvSeries | https://selcukflix.com | `eligible` | Public domain identified, eligible for migration |
| 51 | **SetFilmIzle** | `SetFilmIzle` | 42 | 1 | tr | Movie | https://www.setfilmizle.ltd | `eligible` | Public domain identified, eligible for migration |
| 52 | **SezonlukDizi** | `SezonlukDizi` | 40 | 1 | tr | TvSeries | https://sezonlukdizi.cc | `eligible` | Public domain identified, eligible for migration |
| 53 | **SinemaCX** | `SinemaCX` | 35 | 1 | tr | Movie | https://www.sinema.gg | `eligible` | Public domain identified, eligible for migration |
| 54 | **SineWix** | `SineWix` | 33 | 1 | tr | Movie, TvSeries, Anime, AsianDrama, Cartoon | - | `blocked` | App-only encrypted API without public endpoints |
| 55 | **Sinezy** | `Sinezy` | 40 | 1 | tr | Movie | https://sinezy.to | `eligible` | Public domain identified, eligible for migration |
| 56 | **TrAnimeIzle** | `TrAnimeIzle` | 15 | 1 | tr | Anime | https://www.tranimeizle.io | `eligible` | Public domain identified, eligible for migration |
| 57 | **TurkAnime** | `TurkAnime` | 34 | 1 | tr | Anime, AnimeMovie | https://www.turkanime.tv | `implemented` | Batch 01 revived: EVP AES CBC decryptor + episode scraper |
| 58 | **Turkdizileri** | `Turkdizileri` | 33 | 1 | tr | Movie | https://turkdizileri.tv | `eligible` | Public domain identified, eligible for migration |
| 59 | **TvDiziler** | `TvDiziler` | 18 | 1 | tr | TvSeries | https://tvdiziler.tv | `eligible` | Public domain identified, eligible for migration |
| 60 | **WebDramaTurkey** | `WebDramaTurkey` | 39 | 1 | tr | AsianDrama | https://webdramaturkey2.com | `eligible` | Public domain identified, eligible for migration |
| 61 | **WebteIzle** | `WebteIzle` | 53 | 0 | tr | Movie | https://webteizle.vip | `deprecated` | Marked status=0 (Down) in legacy builds repository |
| 62 | **WFilmizle** | `WFilmizle` | 23 | 1 | tr | Movie | https://www.wfilmizle.pw | `eligible` | Public domain identified, eligible for migration |
| 63 | **YabanciDizi** | `YabanciDizi` | 18 | 1 | tr | TvSeries | https://yabancidizi.news | `eligible` | Public domain identified, eligible for migration |
| 64 | **YeniKaynak** | `YeniKaynak` | 11 | 1 | tr | Movie, TvSeries | https://www.yenikaynak.com | `eligible` | Public domain identified, eligible for migration |
| 65 | **YesilCamTv** | `YesilCamTv` | 9 | 1 | tr | Movie | https://yesilcamtv.com.tr | `eligible` | Public domain identified, eligible for migration |
| 66 | **Youtube** | `Youtube` | 39 | 1 | tr | Movie, Live, Music, TvSeries | https://www.youtube.com | `eligible` | Public domain identified, eligible for migration |
| 67 | **YTS** | `YTS` | 42 | 1 | tr | Torrent | https://web.yts.gg | `eligible` | Public domain identified, eligible for migration |

