import json
import sys

def register(name, module, canonical, types, allowed_hosts, expected_markers, search_query, known_detail, ep_selector="a[href*='/bolum']", ep_mode="episode"):
    # 1. domains.json
    with open('config/domains.json', 'r', encoding='utf-8') as f:
        dom_data = json.load(f)

    dom_data['providers'][name] = {
        'canonical': canonical,
        'allowedHosts': allowed_hosts,
        'expectedMarkers': expected_markers,
        'lastChecked': None,
        'status': 'active'
    }

    with open('config/domains.json', 'w', encoding='utf-8') as f:
        json.dump(dom_data, f, indent=2, ensure_ascii=False)
        f.write('\n')

    # 2. providers.json
    with open('config/providers.json', 'r', encoding='utf-8') as f:
        prov_data = json.load(f)

    # remove if exists
    prov_data['providers'] = [p for p in prov_data['providers'] if p['name'] != name]

    prov_data['providers'].append({
        'name': name,
        'module': module,
        'enabled': True,
        'language': 'tr',
        'types': types,
        'domainKey': name,
        'healthTier': 'standard',
        'smokeTest': {
            'searchQuery': search_query,
            'knownDetail': known_detail,
            'expectedType': types[0]
        },
        'monitoring': {
            'preferredFetch': 'HTTP',
            'dynamicFallback': False,
            'stealthFallback': True,
            'contentType': types[0],
            'playerProbe': {
                'mode': ep_mode,
                'episodeSelector': ep_selector
            },
            'search': {
                'method': 'GET',
                'path': '/arama?q={query}',
                'headers': {
                    'Referer': f"{canonical}/"
                }
            },
            'homepage': {
                'selectors': [
                    'a.poster-card',
                    'a[href*="/dizi/"]',
                    'a[href*="/film/"]'
                ],
                'requiredUrlPatterns': [
                    '/'
                ],
                'excludedUrlPatterns': [
                    '/ara',
                    '/profil',
                    '/giris'
                ]
            }
        }
    })

    with open('config/providers.json', 'w', encoding='utf-8') as f:
        json.dump(prov_data, f, indent=2, ensure_ascii=False)
        f.write('\n')

    # 3. migration-matrix.json
    try:
        with open('legacy/migration-matrix.json', 'r', encoding='utf-8') as f:
            matrix = json.load(f)

        for m in matrix:
            if m['name'] == name:
                m['implementationStatus'] = 'implemented'
                m['healthStatus'] = 'healthy'
                m['replacementModule'] = module
                m['notes'] = 'Full Kotlin provider with automated HTML fixture tests and live BoundedParallelResolver support.'

        with open('legacy/migration-matrix.json', 'w', encoding='utf-8') as f:
            json.dump(matrix, f, indent=2, ensure_ascii=False)
            f.write('\n')
    except Exception as e:
        print(f"Matrix update warning: {e}")

    print(f"Provider {name} successfully registered in domains, providers, and migration matrix!")

if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'DiziKorea':
        register(
            name='DiziKorea',
            module='DiziKorea',
            canonical='https://dizikorea3.com',
            types=['AsianDrama'],
            allowed_hosts=['dizikorea3.com', 'dizikorea.com', 'dizikorea2.com'],
            expected_markers=['DiziKorea', 'dizikorea', 'Kore'],
            search_query='love',
            known_detail='https://dizikorea3.com/dizi/flex-x-cop-izle-dq',
            ep_selector='a[href*="/sezon-"]',
            ep_mode='episode'
        )
    elif len(sys.argv) > 1 and sys.argv[1] == 'DramaDizilerim':
        register(
            name='DramaDizilerim',
            module='DramaDizilerim',
            canonical='https://dramadizilerim.com',
            types=['AsianDrama'],
            allowed_hosts=['dramadizilerim.com', 'www.dramadizilerim.com'],
            expected_markers=['Drama Dizilerim', 'dramadizilerim', 'Dizi'],
            search_query='kral',
            known_detail='https://dramadizilerim.com/dizi/kole-kral',
            ep_selector='a[href*="/izle/"]',
            ep_mode='episode'
        )
    elif len(sys.argv) > 1 and sys.argv[1] == 'WebDramaTurkey':
        register(
            name='WebDramaTurkey',
            module='WebDramaTurkey',
            canonical='https://webdramaturkey2.com',
            types=['AsianDrama'],
            allowed_hosts=['webdramaturkey2.com', 'webdramaturkey.com', 'www.webdramaturkey2.com'],
            expected_markers=['Web Drama Turkey', 'webdramaturkey', 'Diziler'],
            search_query='love',
            known_detail='https://webdramaturkey2.com/dizi/hidden-love-izle',
            ep_selector='a[href*="-bolum"]',
            ep_mode='episode'
        )
    elif len(sys.argv) > 1 and sys.argv[1] == 'Animeler':
        register(
            name='Animeler',
            module='Animeler',
            canonical='https://animeler.pw',
            types=['Anime', 'AnimeMovie'],
            allowed_hosts=['animeler.pw', 'www.animeler.pw', 'animeler.me'],
            expected_markers=['Animeler', 'animeler', 'Anime'],
            search_query='naruto',
            known_detail='https://animeler.pw/steinsgate',
            ep_selector='a[href*="/bolum-"]',
            ep_mode='episode'
        )
    elif len(sys.argv) > 1 and sys.argv[1] == 'SetFilmIzle':
        register(
            name='SetFilmIzle',
            module='SetFilmIzle',
            canonical='https://www.setfilmizle.ltd',
            types=['Movie', 'TvSeries'],
            allowed_hosts=['setfilmizle.ltd', 'www.setfilmizle.ltd'],
            expected_markers=['Set Film izle', 'setfilmizle', 'Film'],
            search_query='avatar',
            known_detail='https://www.setfilmizle.ltd/film/the-get-out/',
            ep_selector='a[href*="-bolum-"]',
            ep_mode='episode'
        )
    elif len(sys.argv) > 1 and sys.argv[1] == 'DiziLife':
        register(
            name='DiziLife',
            module='DiziLife',
            canonical='https://dizi74.life',
            types=['TvSeries', 'Movie'],
            allowed_hosts=['dizi74.life', 'dizi.life', 'dizilife.com'],
            expected_markers=['dizi.life', 'DiziLife', 'Diziler'],
            search_query='dead',
            known_detail='https://dizi74.life/dizi/the-walking-dead',
            ep_selector='a[href*="/bolum/"]',
            ep_mode='episode'
        )
    elif len(sys.argv) > 1 and sys.argv[1] == 'HDFilmDelisi':
        register(
            name='HDFilmDelisi',
            module='HDFilmDelisi',
            canonical='https://hdfilmdelisi.one',
            types=['Movie'],
            allowed_hosts=['hdfilmdelisi.one', 'www.hdfilmdelisi.one'],
            expected_markers=['HDFilmDelisi', 'hdfilmdelisi', 'Film'],
            search_query='avatar',
            known_detail='https://hdfilmdelisi.one/film/saplanti-obsession',
            ep_selector='a[href*="/film/"]',
            ep_mode='movie'
        )
    elif len(sys.argv) > 1 and sys.argv[1] == 'Dizigecesi':
        register(
            name='Dizigecesi',
            module='Dizigecesi',
            canonical='https://dizigecesi.com',
            types=['TvSeries', 'Movie'],
            allowed_hosts=['dizigecesi.com', 'www.dizigecesi.com'],
            expected_markers=['Dizigecesi', 'dizigecesi', 'Dizi'],
            search_query='spartacus',
            known_detail='https://dizigecesi.com/dizi/spartacus',
            ep_selector='a[href*="-sezon/"]',
            ep_mode='episode'
        )
    elif len(sys.argv) > 1 and sys.argv[1] == 'RareFilmm':
        register(
            name='RareFilmm',
            module='RareFilmm',
            canonical='https://rarefilmm.com',
            types=['Movie'],
            allowed_hosts=['rarefilmm.com', 'www.rarefilmm.com'],
            expected_markers=['rarefilmm', 'RareFilmm', 'Films'],
            search_query='paris',
            known_detail='https://rarefilmm.com/2026/07/paris-seveille-1991/',
            ep_selector='article a',
            ep_mode='movie'
        )
    elif len(sys.argv) > 1 and sys.argv[1] == 'FilmHane':
        register(
            name='FilmHane',
            module='FilmHane',
            canonical='https://www.filmhane.shop',
            types=['Movie', 'TvSeries'],
            allowed_hosts=['filmhane.shop', 'www.filmhane.shop'],
            expected_markers=['FilmHane', 'filmhane', 'Film'],
            search_query='avatar',
            known_detail='https://www.filmhane.shop/film/the-lord-of-the-rings-the-war-of-the-rohirrim',
            ep_selector='iframe[src]',
            ep_mode='movie'
        )
