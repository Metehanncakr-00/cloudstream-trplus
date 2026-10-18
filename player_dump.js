/* =============================================================================
 * SetPlayer — özel oynatıcı (hls.js üzerine)
 *
 * NEDEN KENDİ OYNATICIMIZ
 * -----------------------
 * jwplayer çok sesli akışı oynatıyor ama iki şeyi vermiyor: altyazının
 * görünümünü ayarlatmak ve tek kaliteli içerikte kalite menüsünü kendi
 * kurallarımıza göre doldurmak. Ayrıca lisans anahtarına bağlı olması
 * reklam modülünde sessiz kırılmalara yol açıyordu.
 *
 * NE OYNATIYOR
 * ------------
 *   YENİ DÜZEN  çok sesli çok-varyantlı playlist (EXT-X-MEDIA:TYPE=AUDIO)
 *   ESKİ DÜZEN  tek varyantlı master.txt
 * İkisi de aynı koddan geçiyor; fark yalnız hls.js'in bulduğu ses parçası
 * sayısında. Eski kayıtlarda o liste boş kalıyor ve "Dil" menüsü hiç çıkmıyor.
 *
 * ALTYAZI MANİFEST'TEN GELMİYOR
 * -----------------------------
 * Ham `.vtt` adresleri yapılandırmayla veriliyor, dosyayı kendimiz çekip
 * ayrıştırıyoruz ve kendi katmanımızda çiziyoruz. Tarayıcının `<track>`
 * desteğini kullanmamamızın sebebi `::cue`: arka plan kutusu, kenar gölgesi ve
 * konum ayarları tarayıcıdan tarayıcıya tutarsız uygulanıyor, bazılarında hiç
 * uygulanmıyor. Kendi katmanımızda tam denetim var — kullanıcı ayarları da
 * bu yüzden mümkün.
 *
 * ZORUNLU ALTYAZI (`-forced`) MENÜDE GÖRÜNMEZ
 * -------------------------------------------
 * Zorunlu altyazı tam altyazının alternatifi değil: yalnız yabancı dilde
 * konuşulan sahneleri çeviriyor ve SEÇİLEN SESE bağlı. Menüye ayrı satır
 * koymak, izleyiciye "Türkçe" ile "Türkçe (zorunlu)" arasında anlamsız bir
 * seçim sunmak olurdu.
 *
 * Kural: altyazı KAPALIYKEN, seçili sesin dilinde bir `-forced` dosyası varsa
 * kendiliğinden devreye giriyor. Gerçek bir altyazı seçildiğinde geri çekiliyor
 * — tam altyazı zaten o sahneleri de çeviriyor.
 * ========================================================================== */

(function (global) {
	'use strict';

	// -------------------------------------------------------------------------
	// Küçük yardımcılar
	// -------------------------------------------------------------------------

	function el(tag, cls, html) {
		var n = document.createElement(tag);
		if (cls) { n.className = cls; }
		if (html !== undefined) { n.innerHTML = html; }
		return n;
	}

	/**
	 * Saniyeyi `s:dd` ya da `s:dd:dd` biçimine çevirir.
	 *
	 * Bir saatin altında SAAT HANESİ YAZILMIYOR ("3:07", "1:03:07" değil):
	 * katalogun tamamına yakını film ve bölüm, yani sürekli görünen bir "0:"
	 * öneki yer kaplamaktan başka bir şey yapmıyordu.
	 */
	function sure(s) {
		if (!isFinite(s) || s < 0) { s = 0; }
		s = Math.floor(s);
		var sa = Math.floor(s / 3600), dk = Math.floor((s % 3600) / 60), sn = s % 60;
		var iki = function (n) { return n < 10 ? '0' + n : '' + n; };
		return (sa > 0 ? sa + ':' + iki(dk) : dk) + ':' + iki(sn);
	}

	function svg(d, box) {
		return '<svg viewBox="' + (box || '0 0 24 24') + '" aria-hidden="true"><path d="' + d + '"/></svg>';
	}

	/**
	 * İKON SETİ — hepsi 24×24 kutuda ve BENZER GÖRSEL AĞIRLIKTA.
	 *
	 * İlk sette ikonlar farklı kaynaklardan derlenmişti: dişli 20 birim yer
	 * kaplarken oynat 14 birim kaplıyordu ve aynı `width` verilmiş olmasına
	 * rağmen ekranda biri diğerinin bir buçuk katı görünüyordu.
	 *
	 * Kural: her yol yaklaşık 3-21 aralığında kalıyor ve dolgu kalınlıkları
	 * birbirine yakın. Yeni ikon eklerken bu aralığa uydur.
	 */
	var IK = {
		/**
		 * OYNAT / DURDUR / HOPARLÖR BÜYÜTÜLDÜ.
		 *
		 * Aynı 24×24 kutuda olmalarına rağmen ekranda küçük duruyorlardı:
		 * üçgen ve hoparlör kutunun ortasında dar bir alan kaplarken, ±10
		 * oklarının dairesi kutuyu neredeyse tamamen dolduruyor. Görsel denge
		 * `width` ile değil, YOLUN kutuyu ne kadar doldurduğuyla belirleniyor.
		 */
		/**
		 * Yuvarlatılmış köşeli oynat üçgeni.
		 *
		 * Keskin uçlu üçgen küçük boyutta sert duruyor ve arayüzün geri kalanının
		 * yumuşak köşeleriyle çelişiyordu. Üç köşe de yaylandırıldı.
		 */
		oynat:  svg('M6.6 5.1c0-1.2 1.3-2 2.4-1.4l10.3 6.9c1 .6 1 2.2 0 2.8L9 20.3c-1.1.6-2.4-.2-2.4-1.4V5.1z'),
		durdur: svg('M6.2 3.3h4.7v17.4H6.2zm6.9 0h4.7v17.4h-4.7z'),

		geri10:  svg('M12 4.5V1.2L7.2 5.6 12 10V6.6a5.9 5.9 0 1 1-5.9 5.9H3.6A8.4 8.4 0 1 0 12 4.5z'),
		ileri10: svg('M12 4.5V1.2l4.8 4.4L12 10V6.6a5.9 5.9 0 1 0 5.9 5.9h2.5A8.4 8.4 0 1 1 12 4.5z'),

		ses:    svg('M2.6 8.6v6.8h4.6l5.9 5.3V3.3L7.2 8.6H2.6zm15.1 3.4a5.2 5.2 0 0 0-2.9-4.7v9.4a5.2 5.2 0 0 0 2.9-4.7zm-2.9-9.7v2.5a7.8 7.8 0 0 1 0 14.4v2.5a10.3 10.3 0 0 0 0-19.4z'),
		sessiz: svg('M2.6 8.6v6.8h4.6l5.9 5.3V3.3L7.2 8.6H2.6zm18.8 3.4 2.2-2.2-1.7-1.7-2.2 2.2-2.2-2.2-1.7 1.7 2.2 2.2-2.2 2.2 1.7 1.7 2.2-2.2 2.2 2.2 1.7-1.7z'),

		altyazi: svg('M3.6 4.8h16.8a2 2 0 0 1 2 2v10.4a2 2 0 0 1-2 2H3.6a2 2 0 0 1-2-2V6.8a2 2 0 0 1 2-2zm2.6 5.4h3.4V8.6H4.6v6.8h5V13.8H6.2v-3.6zm8.2 0h3.4V8.6h-5v6.8h5V13.8h-3.4v-3.6z'),
		/**
		 * SES DİLİ — MÜZİK NOTASI (jwplayer'ın ses parçası ikonuyla aynı fikir).
		 *
		 * Dünya ikonu "arayüz dili" demek, hoparlör ise ses SEVİYESİ. Seçilen
		 * şey ses PARÇASI olduğu için nota doğru simge; izleyici bu ikonu
		 * başka oynatıcılardan da tanıyor.
		 */
		/**
		 * İKİ NOTA — tek notadan geri dönüldü.
		 *
		 * Tek nota küçük boyutta sapı ince bir çizgiye iniyor ve kutuda boşluk
		 * bırakıyordu; bağlı çift nota hem daha dolu duruyor hem de "ses
		 * parçası" fikrini daha net veriyor.
		 */
		dil:     svg('M9.2 5.3 20 3v10.9a2.9 2.9 0 1 1-1.7-2.6V5.2l-7.4 1.6v10.5a2.9 2.9 0 1 1-1.7-2.6V5.3z'),

		dis:    svg('M19.4 12c0-.5 0-.9-.1-1.4l2-1.5-1.9-3.3-2.3.9c-.7-.6-1.5-1-2.3-1.3L14.4 3h-3.9l-.4 2.4c-.9.3-1.6.7-2.3 1.3l-2.3-.9-1.9 3.3 2 1.5a9.5 9.5 0 0 0 0 2.8l-2 1.5 1.9 3.3 2.3-.9c.7.6 1.4 1 2.3 1.3l.4 2.4h3.9l.4-2.4c.8-.3 1.6-.7 2.3-1.3l2.3.9 1.9-3.3-2-1.5c.1-.5.1-.9.1-1.4zm-7 3.4A3.4 3.4 0 1 1 15.4 12 3.4 3.4 0 0 1 12.4 15.4z'),

		tam:    svg('M4.5 9.4V4.5h4.9v2.2H6.7v2.7H4.5zm10.1-4.9h4.9v4.9h-2.2V6.7h-2.7V4.5zM4.5 14.6h2.2v2.7h2.7v2.2H4.5v-4.9zm12.8 0h2.2v4.9h-4.9v-2.2h2.7v-2.7z'),
		tamCik: svg('M9.4 4.5v4.9H4.5V7.2h2.7V4.5h2.2zm5.2 4.9V4.5h2.2v2.7h2.7v2.2h-4.9zM9.4 19.5v-4.9H4.5v2.2h2.7v2.7h2.2zm5.2-4.9h4.9v2.2h-2.7v2.7h-2.2v-4.9z'),

		/** Yayınla — ekran + dalgalar (Cast/AirPlay için ortak simge). */
		yayin:  svg('M2.4 18.2v3.4h3.4a3.4 3.4 0 0 0-3.4-3.4zm0-4.5v2a5.9 5.9 0 0 1 5.9 5.9h2a7.9 7.9 0 0 0-7.9-7.9zm0-4.5v2a10.4 10.4 0 0 1 10.4 10.4h2A12.4 12.4 0 0 0 2.4 9.2zM20.6 2.4H3.4a1 1 0 0 0-1 1v3.3h2V4.4h15.2v15.2h-6.2v2h7.2a1 1 0 0 0 1-1V3.4a1 1 0 0 0-1-1z'),

		geri:   svg('M14.7 5.6 13.3 4.2l-7.8 7.8 7.8 7.8 1.4-1.4L8.3 12z'),
		tik:    svg('M9.2 16.3 4.9 12l-1.5 1.5 5.8 5.8L20.6 7.9l-1.5-1.5z'),
		ok:     svg('M9.3 5.6 7.9 7l5 5-5 5 1.4 1.4 6.4-6.4z')
	};

	/**
	 * OYNATICI KANITI ARTIK BURADA DEĞİL — `guard.js` üstlendi.
	 *
	 * Kanıt bir dönem bu dosyadaki `xhrSetup` kancasındaydı ve KENDİ
	 * oynatıcımızı kullanan sayfalarda çalışıyordu. `stfplay.php` ise TEMANIN
	 * oynatıcısını çalıştırıyor — o kod bu depoda değil, kanca eklenemiyor ve o
	 * sitelerde bütün videolar 404 alıyordu.
	 *
	 * `guard.js` her sayfanın `<head>`inde, oynatıcıdan önce çalışıyor ve
	 * `XMLHttpRequest`/`fetch` sarmalayıcısıyla hangi oynatıcı olursa olsun
	 * kapsıyor. Tek yerde durması, iki yerin zamanla ayrışmasını da engelliyor.
	 */

	// -------------------------------------------------------------------------
	// WebVTT ayrıştırıcı
	//
	// Tam bir VTT uygulaması DEĞİL, bilinçli olarak: konumlandırma ayarları
	// (`line:`, `position:`, `align:`) yok sayılıyor çünkü altyazıyı biz
	// konumlandırıyoruz ve kullanıcının seçtiği konum kaynaktakini ezmeli.
	//
	// İZİN VERİLEN ETİKETLER `<i> <b> <u>` ile sınırlı. Dosya bizim CDN'imizden
	// geliyor ama içeriği biz üretmiyoruz; ham HTML'i olduğu gibi basmak, bir
	// gün kaynağa müdahale edilirse oynatıcı sayfasında kod çalıştırmaya
	// dönüşürdü.
	// -------------------------------------------------------------------------

	function vttZaman(t) {
		var m = /^(?:(\d+):)?(\d{1,2}):(\d{2})(?:[.,](\d{1,3}))?$/.exec(t.trim());
		if (!m) { return null; }
		return (parseInt(m[1] || 0, 10) * 3600) + (parseInt(m[2], 10) * 60)
		     + parseInt(m[3], 10) + (parseInt(m[4] || 0, 10) / 1000);
	}

	function vttGuvenli(metin) {
		var kacis = metin
			.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

		// Yalnız bu üç etiket geri açılıyor.
		return kacis
			.replace(/&lt;(\/?)(i|b|u)&gt;/gi, '<$1$2>')
			.replace(/&lt;\/?[^&]*?&gt;/g, '');   // <c.xx>, <v Ad> gibi kalanlar atılıyor
	}

	function vttAyristir(metin) {
		var out = [];
		var bloklar = metin.replace(/\r\n?/g, '\n').split(/\n\s*\n/);

		for (var i = 0; i < bloklar.length; i++) {
			var satirlar = bloklar[i].split('\n');
			var zamanSatiri = -1;

			for (var j = 0; j < satirlar.length; j++) {
				if (satirlar[j].indexOf('-->') !== -1) { zamanSatiri = j; break; }
			}
			if (zamanSatiri === -1) { continue; }

			var p = satirlar[zamanSatiri].split('-->');
			var bas = vttZaman(p[0]);
			var son = vttZaman((p[1] || '').split(/\s+/)[0] === '' ? (p[1] || '').trim() : (p[1] || '').trim().split(/\s+/)[0]);

			if (bas === null || son === null) { continue; }

			var govde = satirlar.slice(zamanSatiri + 1).join('\n').trim();
			if (govde === '') { continue; }

			out.push({ b: bas, s: son, m: vttGuvenli(govde) });
		}

		out.sort(function (x, y) { return x.b - y.b; });
		return out;
	}

	// -------------------------------------------------------------------------
	// VAST ayrıştırıcı
	//
	// TAM BİR VAST UYGULAMASI DEĞİL — bilinçli olarak. Desteklenenler:
	//
	//   VAST 2/3/4 Inline    MediaFile, Duration, ClickThrough, skipoffset
	//   Wrapper              VASTAdTagURI, en fazla 3 kademe
	//   Tracking             impression, start, çeyrekler, complete, skip, click
	//
	// VPAID DESTEKLENMİYOR: reklam verenin JavaScript'ini oynatıcının içinde
	// çalıştırmayı gerektiriyor. O kod sayfadaki her şeye erişebiliyor —
	// izleyicinin oturumuna, altyazı adreslerine, manifest jetonuna. Bir
	// oynatıcının kendi güvenliğini reklam ağının insafına bırakması kabul
	// edilebilir değil; VPAID etiketi gelirse reklam atlanıyor ve içerik oynuyor.
	// -------------------------------------------------------------------------

	/** XML metnini belgeye çevirir; bozuksa null. */
	function xmlCoz(metin) {
		try {
			var d = new DOMParser().parseFromString(metin, 'text/xml');
			return d.getElementsByTagName('parsererror').length ? null : d;
		} catch (e) { return null; }
	}

	function xmlMetin(kok, ad) {
		var n = kok.getElementsByTagName(ad);
		return n.length ? (n[0].textContent || '').trim() : '';
	}

	/** "00:00:15" veya "00:00:15.500" → saniye. */
	function vastSure(s) {
		var m = /^(\d+):(\d{2}):(\d{2})(?:\.(\d+))?$/.exec((s || '').trim());
		if (!m) { return 0; }
		return (+m[1]) * 3600 + (+m[2]) * 60 + (+m[3]) + (m[4] ? +('0.' + m[4]) : 0);
	}

	/**
	 * `skipoffset` iki biçimde gelebiliyor: süre ("00:00:05") veya yüzde ("20%").
	 * Yüzde reklamın kendi süresine göre hesaplanıyor.
	 */
	function vastAtla(deger, reklamSuresi) {
		deger = (deger || '').trim();
		if (deger === '') { return -1; }

		if (deger.slice(-1) === '%') {
			var y = parseFloat(deger);
			return isFinite(y) ? (reklamSuresi * y / 100) : -1;
		}

		var s = vastSure(deger);
		return s > 0 ? s : -1;
	}

	/**
	 * MediaFile seçimi.
	 *
	 * Tarayıcının oynatabildiği en yüksek bitrate'li dosya alınıyor —
	 * `canPlayType` ile soruluyor, uzantıya bakılmıyor. Reklam sunucuları
	 * `type` alanını genelde doğru dolduruyor ve webm/mp4 karışık geliyor;
	 * uzantı tahminine güvenmek bazı reklamların sessizce hiç oynamamasına
	 * yol açıyordu.
	 */
	function vastMedya(kok, video) {
		var liste = kok.getElementsByTagName('MediaFile');
		var en = null;

		for (var i = 0; i < liste.length; i++) {
			var m = liste[i];
			var tip = (m.getAttribute('type') || '').toLowerCase();
			var api = (m.getAttribute('apiFramework') || '').toLowerCase();

			// VPAID atlanıyor — sebebi yukarıda.
			if (api === 'vpaid' || tip.indexOf('javascript') !== -1) { continue; }

			if (tip && !video.canPlayType(tip)) { continue; }

			var url = (m.textContent || '').trim();
			if (!/^https?:\/\//i.test(url)) { continue; }

			var bit = parseInt(m.getAttribute('bitrate') || '0', 10) || 0;

			if (!en || bit > en.bit) { en = { url: url, bit: bit }; }
		}

		return en ? en.url : null;
	}

	/** Bir olaya ait izleme adreslerini toplar. */
	function vastIzleme(kok, olay) {
		var out = [];
		var n = kok.getElementsByTagName('Tracking');

		for (var i = 0; i < n.length; i++) {
			if ((n[i].getAttribute('event') || '') === olay) {
				var u = (n[i].textContent || '').trim();
				if (u) { out.push(u); }
			}
		}

		return out;
	}

	/**
	 * İzleme pikselini gönderir.
	 *
	 * `Image` kullanılıyor, `fetch` değil: reklam sunucularının çoğu CORS
	 * başlığı göndermiyor ve `fetch` isteği tarayıcıda hata olarak kesiliyor.
	 * Piksel isteğinde yanıtı okumamız gerekmiyor — gitmesi yeterli.
	 */
	function pikselAt(urls) {
		for (var i = 0; i < (urls || []).length; i++) {
			try { (new Image()).src = urls[i]; } catch (e) {}
		}
	}

	// -------------------------------------------------------------------------
	// Altyazı görünüm ayarları — tarayıcıda saklanıyor
	// -------------------------------------------------------------------------

	var AYAR_ANAHTAR = 'setplayer.altyazi';

	/**
	 * ALTYAZI VARSAYILANLARI.
	 *
	 * Değerler ekranda denenerek seçildi: %100 boyut 1080p'de televizyon
	 * uzaklığından okunmuyor, %50 arka plan açık sahnelerde metni yutuyor ve
	 * sıfır yükseklik altyazıyı kontrol paneline yaklaştırıyordu.
	 *
	 * Yalnız İLK ziyaretçiyi etkiliyor — daha önce ayar yapmış olanın
	 * `localStorage` değeri korunuyor.
	 */
	var VARSAYILAN = { boyut: 160, renk: '#ffffff', arka: 60, kenar: true, konum: 20 };

	function ayarOku() {
		try {
			var d = JSON.parse(localStorage.getItem(AYAR_ANAHTAR) || '{}');
			var o = {};
			for (var k in VARSAYILAN) { o[k] = (d && d[k] !== undefined) ? d[k] : VARSAYILAN[k]; }
			return o;
		} catch (e) { return JSON.parse(JSON.stringify(VARSAYILAN)); }
	}

	function ayarYaz(a) {
		try { localStorage.setItem(AYAR_ANAHTAR, JSON.stringify(a)); } catch (e) {}
	}

	// -------------------------------------------------------------------------
	// Dil tercihi — videolar arasında taşınıyor
	//
	// Altyazı GÖRÜNÜM ayarları (boyut, renk, konum) baştan beri kalıcıydı ama
	// DİL seçimi her videoda sıfırlanıyordu: orijinal dili tercih eden izleyici
	// her filmde menüyü açıp aynı seçimi tekrar yapmak zorundaydı.
	//
	// Ayrı anahtar, `setplayer.altyazi` içine katılmadı: o değerlerin hepsi
	// görünüm ve hiçbiri içeriğe bağlı değil. Dil tercihi ise içerikte karşılığı
	// olmayabilir ve uygulanamayabilir — farklı bir yaşam döngüsü.
	//
	// SAKLANAN ŞEY DİL KODU, İNDİS DEĞİL. Parça sırası her içerikte farklı;
	// "ikinci parça" bir filmde İngilizce, diğerinde Almanca olur.
	// -------------------------------------------------------------------------

	var DIL_ANAHTAR = 'setplayer.dil';

	/**
	 * @return {{ses: string, alt: string|null}} `alt` null = tercih yok,
	 *         '' = kullanıcı altyazıyı KAPATMIŞ (bu da bir tercih)
	 */
	function dilOku() {
		try {
			var d = JSON.parse(localStorage.getItem(DIL_ANAHTAR) || '{}');

			return {
				ses: (d && typeof d.ses === 'string') ? d.ses : '',
				alt: (d && typeof d.alt === 'string') ? d.alt : null
			};
		} catch (e) { return { ses: '', alt: null }; }
	}

	/** Verilen alanları günceller, ötekine dokunmaz. */
	function dilYaz(yeni) {
		var d = dilOku();

		if (yeni.ses !== undefined) { d.ses = yeni.ses; }
		if (yeni.alt !== undefined) { d.alt = yeni.alt; }

		try { localStorage.setItem(DIL_ANAHTAR, JSON.stringify(d)); } catch (e) {}
	}

	// =========================================================================
	// Player
	// =========================================================================

	function SetPlayer(kap, ayar) {
		if (!(this instanceof SetPlayer)) { return new SetPlayer(kap, ayar); }

		this.ayar    = ayar || {};
		this.kap     = kap;
		this.altyazi = ayarOku();

		/** Ses parçaları — hls.js manifest'i çözünce doluyor. */
		this.sesler = [];
		this.sesIx  = -1;

		/** Altyazı parçaları: {file,label,lang,forced} */
		this.altlar  = [];
		this.forced  = [];
		this.altSec  = null;     // seçili altyazının lang'i; null = kapalı
		this.cueler  = null;     // yüklü altyazının ayrıştırılmış hali
		this.cueIx   = -1;
		this.onizleme = null;    // thumbnails cue listesi

		/** Sahte kalite — tek gerçek akış var, menü görünürlük için. */
		this.kaliteler = (this.ayar.qualities && this.ayar.qualities.length)
			? this.ayar.qualities : ['1080p', '720p'];
		/** -1 = otomatik (varsayılan), diğerleri liste indeksi. */
		this.kaliteIx = -1;
		this.kalite = 'Otomatik';

		this.menuYol = [];
		this.gizleZaman = 0;

		this.kur();
	}

	// -------------------------------------------------------------------------
	// Kurulum
	// -------------------------------------------------------------------------

	/**
	 * TELEVIZYON MU?
	 *
	 * Iki yerde gerekiyor: `crossOrigin` (yerli oynatici CORS'a takiliyor) ve
	 * hls.js tampon boyutu (televizyonun bellegi masaustunun onda biri).
	 */
	var SP_TV = /Tizen|Web0S|WebOS|NetCast|SMART-TV|SmartTV|HbbTV|CE-HTML|VIDAA|Vestel|Philips|NETTV|Viera|AQUOS|BRAVIA|InettvBrowser|Opera TV|Espial|Roku|CrKey|Chromecast|GoogleTV|Google TV|AndroidTV|Android TV|AFT[A-Z]|SHIELD|Xbox|PlayStation|Nintendo|Kodi|ExoPlayer|stagefright|DLNA|HiSense|ARRIS|Zeasn|MiBOX|MiTV|DTVPlayer|LG Browser|Maple|NetRangeMMH|SonyBDP|SonyCEBrowser|AppleTV|Apple TV/i.test(navigator.userAgent);

	SetPlayer.prototype.kur = function () {
		var self = this;
		var a = this.ayar;

		this.kok = el('div', 'sp sp--durakli');

		this.renkleriUygula(a);

		this.video = document.createElement('video');
		this.video.playsInline = true;
		this.video.setAttribute('playsinline', '');
		this.video.setAttribute('webkit-playsinline', '');
		/**
		 * `auto` — `metadata` DEGIL.
		 *
		 * `metadata` yalniz sureyi indiriyor, veriyi indirmiyor. hls.js kullanan
		 * tarayicida onemsiz (tampon zaten hls.js'te), ama YERLI HLS oynatan
		 * iPhone ve televizyonda oynatmanin baslamasi `play()` cagrisina kadar
		 * bekliyor: kullanici dugmeye bastiktan SONRA playlist, ardindan ilk
		 * parca iniyor. Bildirilen "cok gec basliyor" tam olarak bu gecikme.
		 */
		this.video.preload = 'auto';
		if (a.poster) { this.video.poster = a.poster; }

		/**
		 * `crossOrigin` ŞART — AMA TELEVİZYONDA DEĞİL.
		 *
		 * Segmentler başka bir alan adından geliyor. Bu bayrak olmadan bazı
		 * tarayıcılar medyayı "kirli" sayıp oynatmayı reddediyor, üstelik
		 * hatayı ağ hatası gibi gösteriyor — teşhisi zor bir kırılma.
		 *
		 * TELEVİZYONLARDA TERSİ OLUYOR. Orada hls.js değil YERLİ HLS çalışıyor
		 * (hls.js kurulamadiginda) ve `crossOrigin` yerli oynatıcıyı
		 * manifest ile her segment için CORS istemeye zorluyor. Tizen ve webOS
		 * medya hattı bunu ayrı bir süreçte yürütüyor; ön istek ya da eksik bir
		 * `Access-Control-Allow-Origin` başlığı videoyu hiç açtırmıyor ve ekrana
		 * yalnız "Video yüklenemedi." düşüyor.
		 *
		 * Bedeli yok: altyazılar ve tuval kullanımı panelin kendi alan adında,
		 * yani televizyonda bu bayrağa ihtiyaç duyan bir şey kalmıyor.
		 */
		if (!SP_TV) { this.video.crossOrigin = 'anonymous'; }

		this.kok.appendChild(this.video);

		/**
		 * BAŞLIK — `basliksiz` ile kapatılabiliyor.
		 *
		 * Tema sayfasında genellikle aynı başlık zaten yazılı ve oynatıcının
		 * üstünde ikinci kez görünüyor. Panelden tek onay kutusuyla
		 * kapatılabiliyor (Ayarlar → Oynatıcı → "Başlığı gizle").
		 */
		if (a.title && !a.basliksiz) {
			this.ust = el('div', 'sp-ust');
			this.ust.textContent = a.title;
			this.kok.appendChild(this.ust);
		}

		this.altyaziKatman = el('div', 'sp-altyazi');
		this.altyaziMetin  = el('div', 'sp-altyazi-metin');
		this.altyaziKatman.appendChild(this.altyaziMetin);
		this.kok.appendChild(this.altyaziKatman);

		// --- orta katman ---
		var orta = el('div', 'sp-orta');
		/**
		 * ORTA OYNAT — VURGU GRADYANIYLA DOLDURULMUŞ.
		 *
		 * Kendi SVG'si var, `IK.oynat` değil. İki sebep:
		 *
		 * 1. Gradyan dolgusu CSS ile yapılamıyor; SVG'nin içinde bir
		 *    `linearGradient` tanımı gerekiyor. Durakları CSS değişkenlerinden
		 *    okuyor, yani panelden seçilen renkler burada da geçerli.
		 *
		 * 2. Bu düğme `sp-dugme` sınıfında DEĞİL, dolayısıyla `fill:
		 *    currentColor` kuralı ona uygulanmıyordu ve SVG'nin varsayılan
		 *    dolgusu devreye giriyordu — yani SİYAH. Siyah zeminde görünmez bir
		 *    düğme; ekranın ortasında hiçbir şey yokmuş gibi duruyordu.
		 *
		 * Gradyan kimliği örneğe özel: aynı sayfada iki oynatıcı bulunursa
		 * ikisi birbirinin tanımını ezmesin.
		 */
		var gid = 'sp-g' + Math.random().toString(36).slice(2, 8);

		this.buyukOynat = el('button', 'sp-buyukoynat',
			'<svg viewBox="0 0 24 24" aria-hidden="true">'
			+ '<defs><linearGradient id="' + gid + '" x1="0" y1="0" x2="1" y2="1">'
			+ '<stop offset="0" stop-color="var(--sp-vurgu2)"/>'
			+ '<stop offset="1" stop-color="var(--sp-vurgu)"/>'
			+ '</linearGradient></defs>'
			+ '<path fill="url(#' + gid + ')" d="M6.6 5.1c0-1.2 1.3-2 2.4-1.4l10.3 6.9c1 .6 1 2.2 0 2.8L9 20.3c-1.1.6-2.4-.2-2.4-1.4V5.1z"/>'
			+ '</svg>');
		this.spinner    = el('div', 'sp-yukleniyor');
		orta.appendChild(this.buyukOynat);
		orta.appendChild(this.spinner);
		this.kok.appendChild(orta);

		this.bilgiKutu = el('div', 'sp-bilgi');
		this.kok.appendChild(this.bilgiKutu);

		this.hataKutu = el('div', 'sp-hata');
		this.kok.appendChild(this.hataKutu);

		this.kontrolKur();
		this.menuKutu = el('div', 'sp-menu');
		this.kok.appendChild(this.menuKutu);

		this.kap.appendChild(this.kok);

		this.olaylar();
		this.altyaziStil();
		this.sesYukle();
		this.parcalariKur();
		this.dugmeleriTazele();

		this.reklamKur();
		this.devKoru();
		this.yukle(a.src);

		// Küçük ekranda arayüz ölçeği
		var olc = function () {
			var g = self.kok.clientWidth;
			self.kok.classList.toggle('sp--kucuk', g < 560);
			self.kok.classList.toggle('sp--buyuk', g > 1400);
		};
		olc();
		if (global.ResizeObserver) { new ResizeObserver(olc).observe(this.kok); }
		else { global.addEventListener('resize', olc); }
	};

	/**
	 * TEMA RENKLERİ — panelden geliyor, oynatıcının TAMAMINI kapsıyor.
	 *
	 * Üç renk yeterli çünkü arayüzdeki her yüzey bu üçünden türetiliyor:
	 *
	 *   vurgu  ilerleme çubuğu, oynat düğmesi, seçili satır, tik, hover,
	 *          kaydırıcı dolgusu, etkin düğme noktası
	 *   zemin  kontrol paneli, menü, ipucu baloncuğu, bilgi kutusu
	 *          (saydamlıkları koddan geliyor — panel daha şeffaf, menü daha opak)
	 *   metin  bütün yazılar ve ikonlar
	 *
	 * Yirmi ayrı renk kutusu sunmak, kullanıcının uyumsuz bir palet kurmasına
	 * ve her yeni ögede birinin unutulmasına yol açardı. Türetme bunu kökten
	 * engelliyor: yeni bir yüzey eklendiğinde rengi kendiliğinden doğru geliyor.
	 *
	 * Değerler sunucuda `#rrggbb` biçimine süzülüyor (admin/pages/player.php);
	 * burada ikinci denetim var, çünkü doğrudan `style` niteliğine yazılıyorlar
	 * ve yapılandırma başka bir yoldan da gelebilir.
	 */
	SetPlayer.prototype.renkleriUygula = function (a) {
		var ok = /^#[0-9a-fA-F]{6}$/;
		var s  = this.kok.style;

		if (ok.test(a.renk  || '')) { s.setProperty('--sp-vurgu',  a.renk); }

		/**
		 * İkinci ton — ilerleme çubuğu ve oynat düğmesindeki geçiş.
		 *
		 * Verilmezse birinciye eşitleniyor: o zaman geçiş kaybolup düz renk
		 * kalıyor, yani gradyanı kapatmak da tek bir renk seçmekle mümkün.
		 */
		s.setProperty('--sp-vurgu2', ok.test(a.renk2 || '') ? a.renk2 : (ok.test(a.renk || '') ? a.renk : '#ff7a18'));

		if (ok.test(a.zemin || '')) {
			var r = parseInt(a.zemin.substr(1, 2), 16);
			var g = parseInt(a.zemin.substr(3, 2), 16);
			var b = parseInt(a.zemin.substr(5, 2), 16);

			/**
			 * SAYDAMLIK KULLANICIYA BIRAKILMIYOR.
			 *
			 * Panelin arkasındaki görüntünün seçilebilmesi arayüzün okunmasını
			 * bozmadan sağlanmalı; bu denge bir kez kurulup sabitlenecek bir
			 * şey. Kullanıcı yalnız TONU seçiyor.
			 */
			s.setProperty('--sp-panel', 'rgba(' + r + ',' + g + ',' + b + ',.74)');
			s.setProperty('--sp-menu',  'rgba(' + r + ',' + g + ',' + b + ',.93)');
			s.setProperty('--sp-ipucu', 'rgba(' + r + ',' + g + ',' + b + ',.97)');
		}

		if (ok.test(a.metin || '')) { s.setProperty('--sp-metin', a.metin); }
	};

	SetPlayer.prototype.kontrolKur = function () {
		var self = this;

		this.kontrol = el('div', 'sp-kontrol');

		// --- ilerleme ---
		this.cubuk = el('div', 'sp-cubuk');
		var ray    = el('div', 'sp-ray');
		this.tampon    = el('div', 'sp-tampon');
		this.ilerleme  = el('div', 'sp-ilerleme');
		this.nokta     = el('div', 'sp-nokta');
		ray.appendChild(this.tampon);
		ray.appendChild(this.ilerleme);
		ray.appendChild(this.nokta);
		this.cubuk.appendChild(ray);

		this.onizlemeKutu = el('div', 'sp-onizleme');
		this.onizlemeKare = el('div', 'sp-onizleme-kare');

		/**
		 * SPRITE İÇ KATMANDA ÖLÇEKLENİYOR.
		 *
		 * Kareler bir sprite sayfasının içinde ve VTT bize yalnız o karenin
		 * kutusunu veriyor (`#xywh=x,y,w,h`) — sayfanın toplam boyutunu değil.
		 * `background-size` ile ölçeklemek o boyutu bilmeyi gerektiriyordu ve
		 * ilk denemede yanlış kare görünüyordu.
		 *
		 * Bu katman TAM KARE BOYUTUNDA (ör. 320×180), arka planı `-x,-y` ile
		 * kaydırılmış; sonra CSS `transform: scale()` ile kutuya sığdırılıyor.
		 * Sprite'ın kaç kare içerdiğini bilmeye gerek kalmıyor.
		 */
		this.onizlemeIc = el('div', 'sp-onizleme-ic');
		this.onizlemeKare.appendChild(this.onizlemeIc);

		this.onizlemeSure = el('div', 'sp-onizleme-sure');
		this.onizlemeKutu.appendChild(this.onizlemeKare);
		this.onizlemeKutu.appendChild(this.onizlemeSure);
		this.cubuk.appendChild(this.onizlemeKutu);

		this.kontrol.appendChild(this.cubuk);

		// --- alt satır ---
		var alt = el('div', 'sp-alt');

		this.dgOynat = el('button', 'sp-dugme sp-dugme--ana', IK.oynat);
		this.dgOynat.dataset.ip = 'Oynat';
		alt.appendChild(this.dgOynat);

		this.dgGeri = el('button', 'sp-dugme', IK.geri10);
		this.dgGeri.dataset.ip = '10 sn geri';
		alt.appendChild(this.dgGeri);

		if (this.ayar.forward !== false) {
			this.dgIleri = el('button', 'sp-dugme', IK.ileri10);
			this.dgIleri.dataset.ip = '10 sn ileri';
			alt.appendChild(this.dgIleri);
		}

		var seskutu = el('div', 'sp-seskutu');
		this.dgSes  = el('button', 'sp-dugme', IK.ses);
		this.dgSes.dataset.ip = 'Sessize al';
		this.sesRay  = el('div', 'sp-sesray');
		this.sesDolu = el('div', 'sp-sesdolu');

		/** Tutamak — kaydırıcının sürüklenebilir olduğunu gösteren tek işaret. */
		this.sesNokta = el('div', 'sp-sesnokta');

		this.sesRay.appendChild(this.sesDolu);
		this.sesRay.appendChild(this.sesNokta);
		seskutu.appendChild(this.dgSes);
		seskutu.appendChild(this.sesRay);
		alt.appendChild(seskutu);

		this.sureYazi = el('span', 'sp-sure', '0:00 / 0:00');
		alt.appendChild(this.sureYazi);

		alt.appendChild(el('div', 'sp-bosluk'));

		/**
		 * ALTYAZI VE DİL — dişlinin yanında kendi düğmeleri.
		 *
		 * İkisi de en sık kullanılan ayar; menünün iki kademe altında durmaları
		 * her seferinde üç tıklama demekti. Düğmeler ilgili alt menüyü DOĞRUDAN
		 * açıyor.
		 *
		 * İçerik desteklemiyorsa gizleniyorlar (`parcalariKur` / `seslerKur`
		 * sonrası `dugmeleriTazele`): eski tek sesli kayıtlarda dil düğmesi,
		 * altyazısı olmayan kayıtlarda altyazı düğmesi hiç görünmüyor.
		 */
		/** Sıra: önce ses dili, sonra altyazı — ses seçimi altyazıyı belirliyor. */
		this.dgDil = el('button', 'sp-dugme', IK.dil);
		this.dgDil.dataset.ip = 'Sesler';
		this.dgDil.style.display = 'none';
		alt.appendChild(this.dgDil);

		this.dgAltyazi = el('button', 'sp-dugme', IK.altyazi);
		this.dgAltyazi.dataset.ip = 'Altyazılar';
		this.dgAltyazi.style.display = 'none';
		alt.appendChild(this.dgAltyazi);

		/**
		 * YAYINLA — cihaz varsa görünür.
		 *
		 * Düğme varsayılan olarak GİZLİ ve yalnız tarayıcı "yakında oynatılabilir
		 * bir cihaz var" dediğinde açılıyor. Her zaman göstermek, tıklayınca
		 * "cihaz bulunamadı" diyen ölü bir düğme demekti.
		 */
		this.dgYayin = el('button', 'sp-dugme', IK.yayin);
		this.dgYayin.dataset.ip = 'Yayınla';
		this.dgYayin.style.display = 'none';
		alt.appendChild(this.dgYayin);

		this.dgMenu = el('button', 'sp-dugme', IK.dis);
		this.dgMenu.dataset.ip = 'Ayarlar';
		alt.appendChild(this.dgMenu);

		this.dgTam = el('button', 'sp-dugme', IK.tam);
		this.dgTam.dataset.ip = 'Tam ekran';
		alt.appendChild(this.dgTam);

		this.kontrol.appendChild(alt);
		this.kok.appendChild(this.kontrol);

		// --- olaylar ---
		this.dgOynat.onclick    = function () { self.cevir(); };
		this.buyukOynat.onclick = function () { self.cevir(); };
		this.dgGeri.onclick     = function () { self.atla(-10); };
		if (this.dgIleri) { this.dgIleri.onclick = function () { self.atla(10); }; }
		this.dgSes.onclick      = function () { self.sessizCevir(); };
		this.dgTam.onclick      = function () { self.tamEkran(); };
		this.dgMenu.onclick     = function (e) { e.stopPropagation(); self.menuAcKapat(); };

		this.dgAltyazi.onclick = function (e) { e.stopPropagation(); self.menuGit('alt'); };
		this.dgDil.onclick     = function (e) { e.stopPropagation(); self.menuGit('ses'); };

		this.cubukOlaylari();
		this.sesOlaylari();
		this.yayinKur();
	};

	/**
	 * YAYINLAMA — Chromecast ve AirPlay, HARİCİ KÜTÜPHANE OLMADAN.
	 *
	 * İki yerli arayüz var ve ikisi de aynı düğmeye bağlanıyor:
	 *
	 *   Remote Playback API   Chrome/Edge — Chromecast cihazlarını buluyor
	 *   WebKit hedef seçici   Safari/iOS — AirPlay
	 *
	 * Google Cast SDK BİLEREK KULLANILMIYOR: harici bir betik yüklemeyi
	 * gerektiriyor (gstatic.com), reklam engelleyicilerin bir kısmı onu
	 * kesiyor ve oynatıcı sayfasına üçüncü taraf kod sokmak istemiyoruz.
	 * Yerli arayüzler aynı cihazlara ulaşıyor.
	 *
	 * DÜĞME YALNIZ CİHAZ VARSA GÖRÜNÜYOR. Tarayıcı ağdaki cihazları izleyip
	 * haber veriyor; "kullanıcının sistemi uyumluysa aktif olsun" kuralı böyle
	 * uygulanıyor.
	 *
	 * SINIR — MSE: hls.js videoyu bellekten beslediğinde (masaüstü Chrome)
	 * uzak oynatma çoğu zaman kullanılamıyor, çünkü cihaza verilecek gerçek bir
	 * adres yok. iOS'ta ise video yerli HLS ile oynadığı için AirPlay
	 * sorunsuz çalışıyor. Bu yüzden düğme masaüstünde çıkmayabilir — eksik
	 * değil, o ortamda yapılabilecek bir şey olmadığı için.
	 */
	SetPlayer.prototype.yayinKur = function () {
		var self = this, v = this.video;

		function goster(acik) { self.dgYayin.style.display = acik ? '' : 'none'; }

		// --- AirPlay (Safari / iOS) ---
		if (window.WebKitPlaybackTargetAvailabilityEvent) {
			v.addEventListener('webkitplaybacktargetavailabilitychanged', function (e) {
				goster(e.availability === 'available');
			});

			v.addEventListener('webkitcurrentplaybacktargetiswirelesschanged', function () {
				self.dgYayin.classList.toggle('sp-dugme--acik', !!v.webkitCurrentPlaybackTargetIsWireless);
			});

			this.dgYayin.onclick = function (e) {
				e.stopPropagation();
				try { v.webkitShowPlaybackTargetPicker(); } catch (err) {}
			};

			return;
		}

		// --- Remote Playback API (Chrome / Edge) ---
		if (!v.remote || typeof v.remote.watchAvailability !== 'function') { return; }

		v.remote.watchAvailability(function (varMi) { goster(varMi); })
			.catch(function () { goster(false); });

		v.remote.addEventListener('connect',    function () { self.dgYayin.classList.add('sp-dugme--acik'); });
		v.remote.addEventListener('disconnect', function () { self.dgYayin.classList.remove('sp-dugme--acik'); });

		this.dgYayin.onclick = function (e) {
			e.stopPropagation();

			/**
			 * `prompt()` KULLANICI ETKİLEŞİMİ İSTİYOR — düğmeden çağrılması bu
			 * yüzden. Reddedilme (kullanıcı vazgeçti, cihaz kayboldu) sessizce
			 * yutuluyor; hata göstermek gereksiz bir uyarı olurdu.
			 */
			v.remote.prompt().catch(function () {});
		};
	};

	// -------------------------------------------------------------------------
	// İlerleme çubuğu
	// -------------------------------------------------------------------------

	SetPlayer.prototype.cubukOlaylari = function () {
		var self = this, tutuluyor = false;

		function oran(e) {
			var r = self.cubuk.getBoundingClientRect();
			var x = (e.touches ? e.touches[0].clientX : e.clientX) - r.left;
			return Math.max(0, Math.min(1, x / r.width));
		}

		function bas(e) {
			tutuluyor = true;
			self.cubuk.classList.add('sp-cubuk--tutuluyor');
			tasi(e);
			e.preventDefault();
		}

		function tasi(e) {
			if (!tutuluyor) { return; }
			var o = oran(e);
			self.ilerleme.style.width = (o * 100) + '%';
			self.nokta.style.left     = (o * 100) + '%';
			if (self.video.duration) { self.sureGoster(o * self.video.duration, self.video.duration); }
		}

		function birak(e) {
			if (!tutuluyor) { return; }
			tutuluyor = false;
			self.cubuk.classList.remove('sp-cubuk--tutuluyor');
			if (self.video.duration) { self.video.currentTime = oran(e.changedTouches ? { clientX: e.changedTouches[0].clientX } : e) * self.video.duration; }
		}

		this.cubuk.addEventListener('mousedown', bas);
		this.cubuk.addEventListener('touchstart', bas, { passive: false });
		document.addEventListener('mousemove', tasi);
		document.addEventListener('touchmove', tasi, { passive: false });
		document.addEventListener('mouseup', birak);
		document.addEventListener('touchend', birak);

		// Önizleme
		this.cubuk.addEventListener('mousemove', function (e) {
			if (!self.video.duration) { return; }

			var o = oran(e);
			var t = o * self.video.duration;
			var r = self.cubuk.getBoundingClientRect();

			self.onizlemeKutu.style.left = Math.max(60, Math.min(r.width - 60, o * r.width)) + 'px';
			self.onizlemeSure.textContent = sure(t);
			self.onizlemeKutu.classList.add('sp-onizleme--acik');

			/**
			 * KARE KUTUSU YALNIZ VERİ VARSA GÖRÜNÜYOR.
			 *
			 * Kutunun sabit ölçüsü ve siyah zemini var; önizleme dosyası
			 * olmayan içerikte (eski düzen, `preview/` klasörü yok) fareyi
			 * çubuğun üstüne getirince BOŞ SİYAH BİR DİKDÖRTGEN çıkıyordu.
			 * İzleyicinin gözünde bu "resim yüklenemedi" demek — oysa o içerikte
			 * hiç önizleme yok. Yalnız zaman göstermek doğru davranış.
			 */
			var varMi = !!(self.onizleme && self.onizleme.length);

			self.onizlemeKutu.classList.toggle('sp-onizleme--karesiz', !varMi);

			if (varMi) { self.onizlemeCiz(t); }
		});

		this.cubuk.addEventListener('mouseleave', function () {
			self.onizlemeKutu.classList.remove('sp-onizleme--acik');
		});
	};

	SetPlayer.prototype.sesOlaylari = function () {
		var self = this, tut = false;

		function uygula(e) {
			var r = self.sesRay.getBoundingClientRect();
			var o = Math.max(0, Math.min(1, ((e.touches ? e.touches[0].clientX : e.clientX) - r.left) / r.width));
			self.video.volume = o;
			self.video.muted  = (o === 0);
		}

		this.sesRay.addEventListener('mousedown', function (e) { tut = true; uygula(e); e.preventDefault(); });
		document.addEventListener('mousemove', function (e) { if (tut) { uygula(e); } });
		document.addEventListener('mouseup', function () { tut = false; });
	};

	// -------------------------------------------------------------------------
	// Genel olaylar
	// -------------------------------------------------------------------------

	/**
	 * OYNAT/DURAKLAT DURUMUNU VIDEODAN OKUR — olaya guvenmez.
	 *
	 * Simge eskiden yalniz `play` ve `pause` olaylariyla degisiyordu ve LG
	 * televizyonda "oynuyor"da takili kaliyordu: `play` olayi geliyor, video
	 * tamponda beklerken duruyor ve karsiliginda `pause` olayi HIC gelmiyor.
	 * Ekranda duraklat simgesi, gercekte duran bir video.
	 *
	 * Otomatik oynatma reddedildiginde de ayni sey oluyordu: `play()` sozu
	 * reddediliyor ama olay cikmiyordu.
	 */
	SetPlayer.prototype.durumCiz = function () {
		if (!this.dgOynat) { return; }

		var durakli = this.video.paused;

		if (durakli) { this.kok.classList.add('sp--durakli'); }
		else         { this.kok.classList.remove('sp--durakli'); }

		this.dgOynat.innerHTML = durakli ? IK.oynat : IK.durdur;
	};

	SetPlayer.prototype.olaylar = function () {
		var self = this, v = this.video;

		v.addEventListener('play',    function () { self.durumCiz(); });
		v.addEventListener('pause',   function () { self.durumCiz(); });
		v.addEventListener('ended',   function () { self.durumCiz(); });
		v.addEventListener('emptied', function () { self.durumCiz(); });
		v.addEventListener('waiting', function () {
			if (self.kok.classList.contains('sp--hata')) { return; }

			self.kok.classList.add('sp--yukleniyor');
		});
		v.addEventListener('playing', function () { self.kok.classList.remove('sp--yukleniyor'); self.durumCiz(); });
		v.addEventListener('canplay', function () { self.kok.classList.remove('sp--yukleniyor'); self.durumCiz(); });

		v.addEventListener('volumechange', function () {
			var o = v.muted ? 0 : v.volume;
			self.sesDolu.style.width  = (o * 100) + '%';
			self.sesNokta.style.left  = (o * 100) + '%';
			self.dgSes.innerHTML = (v.muted || v.volume === 0) ? IK.sessiz : IK.ses;
			self.dgSes.dataset.ip = (v.muted || v.volume === 0) ? 'Sesi aç' : 'Sessize al';

			self.sesKaydet();
		});

		v.addEventListener('timeupdate', function () {
			if (!self.cubuk.classList.contains('sp-cubuk--tutuluyor')) {
				var o = v.duration ? (v.currentTime / v.duration) : 0;
				self.ilerleme.style.width = (o * 100) + '%';
				self.nokta.style.left     = (o * 100) + '%';
				self.sureGoster(v.currentTime, v.duration);
			}

			self.altyaziCiz(v.currentTime);
			self.konumKaydet();
		});

		v.addEventListener('progress', function () {
			if (!v.buffered.length || !v.duration) { return; }
			self.tampon.style.width = ((v.buffered.end(v.buffered.length - 1) / v.duration) * 100) + '%';
		});

		// Videoya tıklama = oynat/durdur (menü açıkken menüyü kapatır)
		v.addEventListener('click', function () {
			if (self.menuKutu.classList.contains('sp-menu--acik')) { self.menuKapat(); return; }
			self.cevir();
		});

		v.addEventListener('dblclick', function () { self.tamEkran(); });

		// Arayüzü gizle/göster
		var hareket = function () {
			self.kok.classList.remove('sp--gizli');
			clearTimeout(self.gizleZaman);
			self.gizleZaman = setTimeout(function () {
				if (!v.paused && !self.menuKutu.classList.contains('sp-menu--acik')) {
					self.kok.classList.add('sp--gizli');
					self.kok.classList.add('sp--kontrolgizli');
				}
			}, 2800);
			self.kok.classList.remove('sp--kontrolgizli');
		};

		/**
		 * FARE HAREKETİ BELGEDEN DİNLENİYOR, KÖKTEN DEĞİL.
		 *
		 * Kök üzerindeki dinleyici TAM EKRANDA sessizce devre dışı kalıyordu:
		 * tam ekran elemanı ayrı bir üst katmana (top layer) taşınıyor ve fare
		 * hareketi kökün kendisine ulaşmadan sonlanabiliyor. İzleyicinin gördüğü
		 * hâliyle: tam ekranda fareyi oynatmak kontrolleri geri getirmiyor — ve
		 * imleç de gizli olduğu için oynatıcı tümüyle donmuş görünüyor.
		 *
		 * Tam ekran DIŞINDA hedef denetleniyor; yoksa sayfanın herhangi bir
		 * yerindeki fare hareketi kontrolleri boş yere açık tutardı.
		 */
		this.fareHareket = function (e) {
			var tam = document.fullscreenElement || document.webkitFullscreenElement || null;
			if (tam !== self.kok && !self.kok.contains(e.target)) { return; }
			hareket();
		};

		document.addEventListener('mousemove', this.fareHareket);
		this.kok.addEventListener('touchstart', hareket);
		hareket();

		document.addEventListener('click', function (e) {
			if (!self.kok.contains(e.target)) { self.menuKapat(); }
		});

		// webkit olayı da dinleniyor: `tamEkran()` Safari'de
		// `webkitRequestFullscreen` kullanabiliyor ve o dalda yalnız
		// `webkitfullscreenchange` tetikleniyor — düğme simgesi takılı kalırdı.
		['fullscreenchange', 'webkitfullscreenchange'].forEach(function (olay) {
			document.addEventListener(olay, function () {
				var tam = document.fullscreenElement || document.webkitFullscreenElement;
				self.dgTam.innerHTML = tam ? IK.tamCik : IK.tam;
			});
		});

		this.kok.tabIndex = 0;
		this.kok.addEventListener('keydown', function (e) { self.tus(e); });
	};

	SetPlayer.prototype.tus = function (e) {
		var v = this.video;

		switch (e.key) {
			case ' ': case 'k':      this.cevir(); break;
			case 'ArrowRight':       this.atla(10); break;
			case 'ArrowLeft':        this.atla(-10); break;
			case 'ArrowUp':          v.volume = Math.min(1, v.volume + .1); break;
			case 'ArrowDown':        v.volume = Math.max(0, v.volume - .1); break;
			case 'f':                this.tamEkran(); break;
			case 'm':                this.sessizCevir(); break;
			case 'c':                this.altyaziGecis(); break;
			default: return;
		}

		e.preventDefault();
	};

	SetPlayer.prototype.sureGoster = function (a, b) {
		/**
		 * BOŞLUK `gap` İLE VERİLİYOR, METİNLE DEĞİL.
		 *
		 * `<b>32:20</b><span> / 2:36:29</span>` yazıldığında span'in başındaki
		 * boşluk kayboluyordu: flex kutusunda her çocuk kendi satır bağlamını
		 * kuruyor ve satır başındaki boşluk daraltılıyor. Sonuç `32:20/ 2:36:29`
		 * — bölü işareti geçen süreye yapışık.
		 */
		this.sureYazi.innerHTML = '<b>' + sure(a) + '</b><span>/</span><span>' + sure(b || 0) + '</span>';
	};

	// -------------------------------------------------------------------------
	// Akış
	// -------------------------------------------------------------------------

	/** hls.js hata nesnesinden ekranda gösterilecek kısa kod. */
	function spHataKodu(d) {
		if (!d) { return ''; }

		var kod = d.details || d.type || '';
		var http = (d.response && d.response.code) ? d.response.code : 0;

		return http ? (kod + ' ' + http) : kod;
	}

	/** Yerli oynatıcının kendi hata kodu. */
	function spYerliKodu(v) {
		var e = v && v.error;
		return e ? ('yerli-' + e.code) : 'yerli';
	}

	SetPlayer.prototype.bekciDur = function () {
		if (!this._yerliBekci) { return; }

		global.clearTimeout(this._yerliBekci);
		this._yerliBekci = null;
	};

	SetPlayer.prototype.yukle = function (src) {
		var self = this, v = this.video;

		this.bekciDur();

		if (!src) { return this.hataGoster('Kaynak adresi yok.'); }

		var yerli = v.canPlayType('application/vnd.apple.mpegurl');

		/**
		 * hls.js ÖNCELİKLİ, yerli HLS geriye düşüş.
		 *
		 * Safari yerli HLS oynatıyor ama ses parçası listesini JavaScript'e
		 * `audioTracks` ile veriyor ve o arayüz iOS'ta eksik. hls.js
		 * destekleniyorsa onu kullanmak, dil menüsünün her yerde aynı çalışması
		 * demek. Desteklenmiyorsa (iOS Safari) yerli oynatmaya düşülüyor ve dil
		 * seçimi tarayıcının kendi menüsüne kalıyor.
		 */
		if (global.Hls && global.Hls.isSupported() && !this._zorlaYerli) {
			var hls = new global.Hls({
				enableWorker: true,
				lowLatencyMode: false,
				backBufferLength: SP_TV ? 30 : 90,

				/**
				 * TAMPON — donmaya karşı en ucuz savunma.
				 *
				 * hls.js `max(8 × maxBufferSize ÷ bitrate, maxBufferLength)`
				 * hesaplıyor; varsayılan 30 sn, yüksek bitrate'te tamponu tam
				 * o değere düşürüyor — yani en çok bayt isteyen içerik en ince
				 * tamponla oynuyor. Buradaki değer alt sınır olduğu için her
				 * bitrate'te garanti.
				 */
				maxBufferLength: this.ayar.buffer || (SP_TV ? 30 : 90),

				/**
				 * TELEVIZYONDA TAVAN DA DUSURULUYOR.
				 *
				 * hls.js varsayilani 60 MB. Televizyonun tarayicisina ayrilan bellek
				 * masaustunun onda biri kadar; 60 MB'lik tampon orada sekmeyi
				 * dusuruyor ve video ortasinda kendiliginden duruyor.
				 */
				maxBufferSize: SP_TV ? 20 * 1000 * 1000 : 60 * 1000 * 1000,

				fragLoadingMaxRetry: 6,
				manifestLoadingMaxRetry: 4,
				levelLoadingMaxRetry: 4,

				/**
				 * HER İSTEĞİN ÖNÜNDE GELİŞTİRİCİ ARAÇLARI DENETİMİ.
				 *
				 * NEDEN BURADA — ZAMANLAYICI KAÇINILMAZ OLARAK GEÇ KALIYOR
				 * -------------------------------------------------------
				 * `guard.js` içindeki sürekli izleme yarım saniyede bir nabız
				 * atıyor ve iki sessiz tur bekliyor: araçlar açıldıktan sonra
				 * tetiklenmesi ~1 saniye sürüyor. O bir saniye boş geçmiyor —
				 * hls.js segment indirmeye devam ediyor ve adresler ağ
				 * sekmesine düşüyor. Bildirilen ihlal tam olarak buydu.
				 *
				 * Zamanlayıcıyı hızlandırmak çözüm DEĞİL: ne kadar sıklaştırılsa
				 * da iki tur arasında bir pencere kalıyor ve o pencerede çıkan
				 * her istek kaydediliyor.
				 *
				 * `xhrSetup` hls.js'in HER isteğinden (master, varyant, segment,
				 * ses parçası) hemen önce, istek AÇILMADAN çağrılıyor. Denetim
				 * buraya konunca sızabilecek segment sayısı SIFIR oluyor.
				 *
				 * ASIL DENETİM ARTIK `guard.js` İÇİNDE — burası yedek katman.
				 * Bu kanca yalnız KENDİ oynatıcımızı kapsıyor; `stfplay.php`
				 * temanın oynatıcısını çalıştırdığı ve orada `xhrSetup`
				 * bulunmadığı için o sayfalarda birkaç segment sızıyordu.
				 * Denetim `guard.js`teki `XMLHttpRequest` sarmalayıcısına taşındı
				 * ve artık her iki oynatıcıyı da kapsıyor.
				 *
				 * BURASI YİNE DE DURUYOR: `xhrSetup`, `send`ten de erken
				 * çalışıyor ve `guard.js` basılmayan bir sayfada (panel
				 * önizlemesi, geliştirme kopyası) tek denetim bu oluyor.
				 * İkisinin birlikte çalışması yalnız bir ölçüm tekrarı — araçlar
				 * kapalıyken ikisi de 1 ms'nin altında.
				 *
				 * ATMAK GEREKİYOR, `return false` YETMİYOR: hls.js dönüş
				 * değerine bakmıyor. Hata fırlatınca istek kurulmadan iptal
				 * oluyor; zaten `SPG.kontrol()` o sırada oynatıcıyı söküp
				 * sayfadan çıkarmış oluyor, yani yeniden deneme de olmuyor.
				 *
				 * MALİYETİ YOK: araçlar kapalıyken ölçüm 1 ms'nin altında ve
				 * segmentler saniyeler arayla iniyor.
				 *
				 * iOS'ta bu kanca ÇALIŞMIYOR (yerli HLS oynatıyor, `xhrSetup`
				 * devrede değil) — ama orada geliştirici araçları da yok ve
				 * `guard.js` mobilde zaten susuyor.
				 */
				xhrSetup: function (xhr, url) {
					if (global.SPG && typeof global.SPG.kontrol === 'function'
					    && !global.SPG.kontrol()) {
						throw new Error('engellendi');
					}

					/**
					 * İNDİRME YÖNETİCİSİ KANITI BURADA DEĞİL — `guard.js`'te.
					 *
					 * Bu kanca yalnız KENDİ oynatıcımızı kapsıyor; `stfplay.php`
					 * temanın oynatıcısını çalıştırdığı için oradan geçmiyor.
					 * Kanıt `guard.js` içindeki `XMLHttpRequest` sarmalayıcısına
					 * taşındı ve artık her iki oynatıcıyı da kapsıyor.
					 */
				}
			});

			this.hls = hls;

			hls.on(global.Hls.Events.MANIFEST_PARSED, function () {
				self.seslerKur(hls.audioTracks || []);
				self.basla();
			});

			/**
			 * SES LİSTESİ AYRI BİR OLAYLA DA GELİYOR — ikisi de dinleniyor.
			 *
			 * `MANIFEST_PARSED` anında `hls.audioTracks` her sürümde dolu
			 * olmuyor: ses grubu ayrı bir denetleyici tarafından işleniyor ve
			 * listeyi `AUDIO_TRACKS_UPDATED` ile bildiriyor. Yalnız ilkini
			 * dinlemek, çok sesli içerikte "Dil" menüsünün hiç çıkmaması
			 * demekti — video sorunsuz oynadığı için sebebi de görünmüyordu.
			 *
			 * `seslerKur` yeniden çağrılabilir: seçili parçayı koruyor.
			 */
			if (global.Hls.Events.AUDIO_TRACKS_UPDATED) {
				hls.on(global.Hls.Events.AUDIO_TRACKS_UPDATED, function (_e, d) {
					self.seslerKur((d && d.audioTracks) || hls.audioTracks || []);
					self.menuTazele();
				});
			}

			hls.on(global.Hls.Events.AUDIO_TRACK_SWITCHED, function (_e, d) {
				self.sesIx = d.id;
				self.altyaziOtomatik();
				self.menuTazele();
			});

			hls.on(global.Hls.Events.ERROR, function (_e, d) {
				if (!d.fatal) { return; }

				if (d.type === global.Hls.ErrorTypes.NETWORK_ERROR) {
					self._agHata = (self._agHata || 0) + 1;

					if (self._agHata <= 3) { hls.startLoad(); return; }

					hls.destroy();
					self.hls = null;

					if (yerli && !self.yerli && !self._yerliDustu) { self._zorlaYerli = true; self.yukle(src); return; }

					self.hataGoster('Video yüklenemedi.', spHataKodu(d));
					return;
				}

				if (d.type === global.Hls.ErrorTypes.MEDIA_ERROR) {
					self._medyaHata = (self._medyaHata || 0) + 1;
					if (self._medyaHata <= 2) { hls.recoverMediaError(); return; }
				}

				hls.destroy();
				self.hls = null;
				if (yerli && !self.yerli && !self._yerliDustu) { self._zorlaYerli = true; self.yukle(src); return; }
				self.hataGoster('Video yüklenemedi.', spHataKodu(d));
			});

			hls.loadSource(src);
			hls.attachMedia(v);

		} else if (yerli) {
			/**
			 * YERLİ HLS — iPhone ve eski Safari.
			 *
			 * Ses ve altyazı parçaları manifest'ten geliyor ve tarayıcı onları
			 * `audioTracks` / `textTracks` üzerinden sunuyor. Bizim menülerimiz
			 * aynı listeyi gösterip aynı arayüzü sürüyor — izleyici için fark
			 * yok. Tam ekrana geçildiğinde Apple'ın kendi seçiciler de aynı
			 * parçaları gösteriyor, çünkü kaynak aynı: manifest.
			 */
			this.yerli = true;

			v.src = src;

			self._yerliBekci = global.setTimeout(function () {
				self._yerliBekci = null;

				if (self._yerliDustu || v.readyState >= 1) { return; }
				if (!global.Hls || !global.Hls.isSupported()) { self.hataGoster('Video yüklenemedi.', 'yerli-takildi'); return; }

				self._yerliDustu = true;
				self.yerli = false;

				try { v.removeAttribute('src'); v.load(); } catch (e) {}

				self.yukle(src);
			}, 9000);

			var kurulum = function () {
				self.seslerKur(v.audioTracks ? Array.prototype.slice.call(v.audioTracks) : []);
				self.altyaziOtomatik();
				self.dugmeleriTazele();
			};

			v.addEventListener('loadedmetadata', function () { self.bekciDur(); kurulum(); self.basla(); }, { once: true });
			v.addEventListener('error', function () {
				self.bekciDur();

				if (!self._zorlaYerli && !self._yerliDustu && global.Hls && global.Hls.isSupported()) {
					self._yerliDustu = true;
					self.yerli = false;
					try { v.removeAttribute('src'); v.load(); } catch (e) {}
					self.yukle(src);
					return;
				}
				self.hataGoster('Video yüklenemedi.', spYerliKodu(v));
			}, { once: true });

			/**
			 * Parça listeleri metadata'dan SONRA da değişebiliyor: WebKit
			 * altyazı parçalarını playlist indikçe ekliyor. Tek sefer dinlemek,
			 * geç gelen dillerin menüde hiç görünmemesi demekti.
			 */
			if (v.textTracks && v.textTracks.addEventListener) {
				v.textTracks.addEventListener('addtrack', kurulum);
			}
			if (v.audioTracks && v.audioTracks.addEventListener) {
				v.audioTracks.addEventListener('addtrack', kurulum);
			}

		} else {
			this.hataGoster('Tarayıcınız bu yayını desteklemiyor.');
		}
	};

	SetPlayer.prototype.basla = function () {
		var self = this;

		/**
		 * KALDIĞI YERDEN DEVAM — `loadedmetadata` BEKLENİYOR.
		 *
		 * Kod buradaydı ama hiç çalışmıyordu: `basla()` manifest çözülünce
		 * çağrılıyor ve o anda `video.duration` henüz NaN. Koşuldaki
		 * `k < duration - 15` karşılaştırması NaN ile her zaman false dönüyor,
		 * yani atlama sessizce hiç yapılmıyordu.
		 *
		 * Süre ancak `loadedmetadata` ile geliyor; atlama oraya taşındı.
		 *
		 * SON 15 SANİYE ATLANMIYOR: filmi bitirmiş birini jeneriğin ortasına
		 * düşürmek "baştan izleyeyim" niyetini bozar.
		 */
		var devam = function () {
			var k = self.konumOku();

			if (k > 5 && self.video.duration && k < self.video.duration - 15) {
				self.video.currentTime = k;
				self.bilgi('Kaldığın yerden devam');
			}
		};

		if (this.video.readyState >= 1) { devam(); }
		else { this.video.addEventListener('loadedmetadata', devam, { once: true }); }

		this.altyaziOtomatik();

		/**
		 * ÖN REKLAM İÇERİKTEN ÖNCE.
		 *
		 * Manifest çözüldükten sonra tetikleniyor: reklam oynarken içeriğin
		 * tamponu arka planda dolmaya devam ediyor ve reklam bitince video
		 * beklemeden başlıyor.
		 */
		if (this.ayar.reklam && this.ayar.reklam.tag && !this.onReklamGosterildi) {
			this.onReklamGosterildi = true;
			this.reklamGoster(this.ayar.reklam.tag);
			return;
		}

		if (this.ayar.autostart) {
			var p = this.video.play();
			if (p && p.catch) { p.catch(function () { self.durumCiz(); }); }
		}
	};

	// -------------------------------------------------------------------------
	// Ses parçaları
	// -------------------------------------------------------------------------

	/**
	 * Ses parçalarını kaydeder — BİRDEN ÇOK KEZ ÇAĞRILABİLİR.
	 *
	 * hls.js listeyi iki ayrı olayla bildirebiliyor (`MANIFEST_PARSED` ve
	 * `AUDIO_TRACKS_UPDATED`); ikisi de bu yöntemi çağırıyor. Bu yüzden seçili
	 * parça KORUNUYOR: her çağrıda sıfırlansaydı, izleyici İngilizce seçtikten
	 * hemen sonra gelen ikinci olay onu Türkçeye geri döndürürdü.
	 */
	SetPlayer.prototype.seslerKur = function (liste) {
		var oncekiSayi = this.sesler.length;

		this.sesler = [];

		for (var i = 0; i < liste.length; i++) {
			var t = liste[i];

			this.sesler.push({
				/**
				 * Yerli `AudioTrack.id` METİN olabiliyor (WebKit boş dize
				 * veya "1" döndürüyor); sayısal değilse dizi indeksi
				 * kullanılıyor. Karışırsa seçim hep ilk parçaya düşerdi.
				 */
				ix:    (typeof t.id === 'number') ? t.id : i,

				/**
				 * Ad sırası: manifest'teki NAME → dil kodundan üretilen ad →
				 * sıra numarası. `NAME` bizim ürettiğimiz playlist'te zaten
				 * "Türkçe"/"English" olarak geliyor.
				 */
				ad:    t.name || t.label || (t.lang || t.language || '').toUpperCase() || ('Ses ' + (i + 1)),
				lang:  String(t.lang || t.language || '').toLowerCase()
			});
		}

		/**
		 * Seçili parça: hls.js ne diyorsa o. İlk kurulumda -1 dönebiliyor
		 * (henüz seçim yapılmamış) — o durumda ilk parça varsayılan.
		 */
		if (this.hls) {
			var a = this.hls.audioTrack;
			this.sesIx = (a !== undefined && a !== null && a >= 0)
				? a
				: (this.sesler.length ? this.sesler[0].ix : -1);
		} else if (oncekiSayi === 0) {
			this.sesIx = this.sesler.length ? this.sesler[0].ix : -1;
		}

		/**
		 * KAYITLI DİL TERCİHİ — yalnız İLK kurulumda.
		 *
		 * İzleyici bir videoda orijinal dili seçtiyse sonraki videoda da onu
		 * bekliyor. Tercih `dilOku()` ile localStorage'dan geliyor ve bu
		 * içerikte karşılığı varsa uygulanıyor; yoksa hiçbir şey yapılmıyor
		 * (dublajı olmayan bir film "İspanyolca yok" diye hata vermemeli).
		 *
		 * `oncekiSayi === 0` KOŞULU ŞART: hls.js listeyi iki olayla bildiriyor
		 * (`MANIFEST_PARSED`, `AUDIO_TRACKS_UPDATED`) ve her ikisinde de
		 * uygulasaydık, izleyici menüden başka bir dil seçtikten hemen sonra
		 * gelen ikinci olay onu kayıtlı dile geri döndürürdü.
		 */
		if (oncekiSayi === 0) { this.kayitliDiliUygula(); }

		this.altyaziOtomatik();
		this.dugmeleriTazele();
	};

	/**
	 * Kayıtlı ses dilini bu içerikte arar ve varsa seçer.
	 *
	 * `sesSec()` ÇAĞRILMIYOR, doğrudan seçim yapılıyor: `sesSec()` tercihi
	 * KAYDEDİYOR ve buradan çağırmak, kullanıcının hiç dokunmadığı bir seçimi
	 * onun tercihi gibi yazmak olurdu. Fark, dublajı olmayan bir içerikte
	 * ortaya çıkar: orada seçim orijinal dile düşer ve o dil kalıcı tercih
	 * olarak kaydedilirse, sonraki filmlerde Türkçe dublaj varken bile
	 * orijinal dil açılırdı.
	 */
	SetPlayer.prototype.kayitliDiliUygula = function () {
		var d = dilOku();
		if (!d.ses || this.sesler.length < 2) { return; }

		for (var i = 0; i < this.sesler.length; i++) {
			if (this.sesler[i].lang !== d.ses) { continue; }

			var ix = this.sesler[i].ix;

			if (this.hls) { this.hls.audioTrack = ix; }
			else if (this.video.audioTracks) {
				for (var j = 0; j < this.video.audioTracks.length; j++) {
					this.video.audioTracks[j].enabled = (j === ix);
				}
			}

			this.sesIx = ix;
			return;
		}
	};

	SetPlayer.prototype.sesSec = function (ix) {
		/**
		 * KULLANICI SEÇİMİ KAYDEDİLİYOR — sonraki videolarda da uygulanacak.
		 *
		 * Dil kodu `ix` üzerinden bulunuyor, `sesDili()` ile değil: hls.js'te
		 * `audioTrack` yazması asenkron ve `sesIx` bu satırda henüz eski değeri
		 * taşıyor olabiliyor.
		 */
		for (var k = 0; k < this.sesler.length; k++) {
			if (this.sesler[k].ix === ix) { dilYaz({ ses: this.sesler[k].lang }); break; }
		}

		if (this.hls) { this.hls.audioTrack = ix; }
		else if (this.video.audioTracks) {
			for (var i = 0; i < this.video.audioTracks.length; i++) {
				this.video.audioTracks[i].enabled = (i === ix);
			}
			this.sesIx = ix;
			this.altyaziOtomatik();
		}

		this.menuTazele();
	};

	/** Seçili sesin dil kodu. */
	SetPlayer.prototype.sesDili = function () {
		for (var i = 0; i < this.sesler.length; i++) {
			if (this.sesler[i].ix === this.sesIx) { return this.sesler[i].lang; }
		}
		return '';
	};

	// -------------------------------------------------------------------------
	// Altyazı
	// -------------------------------------------------------------------------

	SetPlayer.prototype.parcalariKur = function () {
		var t = this.ayar.tracks || [];

		for (var i = 0; i < t.length; i++) {
			var p = t[i];

			if (p.kind === 'thumbnails') { this.onizlemeYukle(p.file); continue; }

			if (p.forced) { this.forced.push(p); }
			else          { this.altlar.push(p); }
		}
	};

	/**
	 * Altyazı seçer — KULLANICI eylemi.
	 *
	 * @param {string|null} lang null = "Kapalı"
	 */
	SetPlayer.prototype.altyaziSec = function (lang) {
		this.altSec = lang;

		/**
		 * KULLANICI SEÇİMİ KAYDEDİLİYOR — "Kapalı" da bir seçim.
		 *
		 * `null` (tercih yok) ile `''` (kapalı seçildi) ayrı tutuluyor: ikisini
		 * aynı değere indirseydik, altyazıyı bilerek kapatan izleyicinin
		 * tercihi "hiç seçim yapmamış" sayılır ve sonraki videoda otomatik
		 * kural Türkçe altyazıyı geri açardı.
		 */
		dilYaz({ alt: (lang === null) ? '' : lang });

		this.altyaziUygula();
		this.menuTazele();
		this.dugmeleriTazele();
	};

	/**
	 * "KAPALI" GERÇEKTEN KAPALI DEĞİL — ZORUNLU ALTYAZI DEMEK.
	 *
	 * Zorunlu altyazı (`<dil>-forced.vtt`) tam altyazının alternatifi değil:
	 * yalnız YABANCI DİLDE konuşulan sahneleri çeviriyor ve seçili SESE bağlı.
	 * Türkçe dublajlı izleyen biri o sahnelerde ne söylendiğini anlamak zorunda
	 * — bu yüzden "Kapalı" seçildiğinde bile devreye giriyor ve menüde ayrı bir
	 * satır olarak GÖRÜNMÜYOR.
	 *
	 * Gerçek bir altyazı seçildiğinde zorunlu geri çekiliyor: tam altyazı o
	 * sahneleri zaten çeviriyor, ikisi üst üste binerse metin okunmaz oluyor.
	 */
	SetPlayer.prototype.altyaziUygula = function () {
		var ara = (this.altSec === null) ? this.sesDili() : this.altSec;

		/**
		 * iOS / YERLİ OYNATMA — altyazıyı TARAYICI çiziyor.
		 *
		 * iPhone'da hls.js çalışmıyor ve video Apple'ın yerli HLS'i ile
		 * oynuyor. Orada altyazı manifest'teki `EXT-X-MEDIA:TYPE=SUBTITLES`
		 * parçalarından geliyor ve `video.textTracks` üzerinden yönetiliyor.
		 *
		 * Kendi katmanımızı da çalıştırsaydık EKRANDA İKİ ALTYAZI olurdu —
		 * biri bizim, biri tarayıcının. Bu yüzden yerli kipte VTT indirmiyoruz;
		 * yalnız doğru parçayı `showing` yapıyoruz.
		 *
		 * Karşılığında görünüm ayarları iOS'ta çalışmıyor: orada altyazı
		 * biçimlendirmesi işletim sistemi ayarlarından geliyor (Ayarlar →
		 * Erişilebilirlik → Altyazılar). Bunu ezmeye çalışmak yerine sisteme
		 * bırakmak doğrusu.
		 */
		if (this.yerli) {
			var tt = this.video.textTracks || [];
			var zorunlu = (this.altSec === null);

			for (var t = 0; t < tt.length; t++) {
				var uyar = (String(tt[t].language || '').toLowerCase() === ara);

				/**
				 * Zorunlu parçayı ayırmak için `label` kullanılıyor: HLS
				 * `FORCED=YES` bilgisini `textTracks` arayüzü ayrı bir alanla
				 * vermiyor, ama biz adı "… (zorunlu)" olarak üretiyoruz.
				 */
				var bu = /zorunlu/i.test(String(tt[t].label || ''));

				tt[t].mode = (uyar && bu === zorunlu) ? 'showing' : 'disabled';
			}

			return;
		}

		var liste = (this.altSec === null) ? this.forced : this.altlar;
		var p     = null;

		for (var i = 0; i < liste.length; i++) {
			if (liste[i].lang === ara) { p = liste[i]; break; }
		}

		this.vttYukle(p ? p.file : null);
	};

	/**
	 * SES DEĞİŞTİĞİNDE ALTYAZI KENDİLİĞİNDEN AYARLANIYOR.
	 *
	 * Kural, izleyicinin gerçekten isteyeceği şeyi varsayıyor:
	 *
	 *   Türkçe ses    → altyazı KAPALI   (dublajlı izliyor, altyazı gereksiz;
	 *                                     yabancı sahneler için zorunlu devrede)
	 *   Orijinal ses  → TÜRKÇE altyazı   (orijinal dilde izliyor, çeviri lazım)
	 *
	 * Bu her ses değişiminde uygulanıyor — AYNI OTURUMDA kullanıcının önceki
	 * altyazı seçimi korunmuyor. Bilinçli: "İngilizce sese geçtim ama altyazı
	 * hâlâ kapalı" durumu, her geçişte elle düzeltilmesi gereken bir sürtünme
	 * yaratırdı. Seçim yine de tek tıklık — menü açık kalıyor.
	 *
	 * KAYITLI TERCİH KURALIN ÖNÜNE GEÇİYOR. İzleyici daha önce bir altyazı dili
	 * seçtiyse (ya da bilerek kapattıysa) sonraki videolarda o uygulanıyor;
	 * yukarıdaki kural yalnız hiç tercih yapmamış izleyici için çalışıyor.
	 * Tercihi görmezden gelmek, her filmde aynı iki tıkı tekrarlatmak demekti.
	 */
	SetPlayer.prototype.altyaziOtomatik = function () {
		var d = dilOku();

		if (d.alt !== null) {
			/**
			 * '' = kullanıcı KAPALI seçmiş. Kayıtlı dil bu içerikte yoksa da
			 * kapalıya düşülüyor: olmayan bir altyazıyı zorlamak yerine, bir
			 * sonraki seçim yine kaydedilecek.
			 */
			var bulundu = null;

			for (var k = 0; k < this.altlar.length; k++) {
				if (this.altlar[k].lang === d.alt) { bulundu = d.alt; break; }
			}

			this.altSec = bulundu;
			this.altyaziUygula();
			this.dugmeleriTazele();
			return;
		}

		var dil = this.sesDili();

		if (dil === 'tur') { this.altSec = null; }
		else {
			var turVar = false;
			for (var i = 0; i < this.altlar.length; i++) {
				if (this.altlar[i].lang === 'tur') { turVar = true; break; }
			}
			this.altSec = turVar ? 'tur' : null;
		}

		this.altyaziUygula();
		this.dugmeleriTazele();
	};

	SetPlayer.prototype.vttYukle = function (url) {
		var self = this;

		this.cueler = null;
		this.cueIx  = -1;
		this.altyaziMetin.innerHTML = '';

		if (!url) { return; }

		this.vttSon = url;

		fetch(url, { credentials: 'omit' })
			.then(function (r) { return r.ok ? r.text() : null; })
			.then(function (metin) {
				// Bu istek sürerken başka bir altyazı seçilmiş olabilir.
				if (metin === null || self.vttSon !== url) { return; }
				self.cueler = vttAyristir(metin);
				self.dugmeleriTazele();
				self.altyaziCiz(self.video.currentTime);
			})
			.catch(function () {});
	};

	SetPlayer.prototype.altyaziCiz = function (t) {
		if (!this.cueler) { return; }

		/**
		 * Arama ileri doğru yürüyor ve son bulunan yerden devam ediyor.
		 * Her karede 2000 cue'yu baştan taramak, uzun filmlerde gözle görülür
		 * bir yavaşlama üretiyordu.
		 */
		var c = this.cueler, n = c.length, ix = -1;

		if (this.cueIx >= 0 && this.cueIx < n && c[this.cueIx].b <= t && t <= c[this.cueIx].s) {
			return;   // hâlâ aynı cue, DOM'a dokunma
		}

		for (var i = 0; i < n; i++) {
			if (c[i].b <= t && t <= c[i].s) { ix = i; break; }
			if (c[i].b > t) { break; }
		}

		if (ix === this.cueIx) { return; }

		this.cueIx = ix;
		this.altyaziMetin.innerHTML = (ix >= 0) ? c[ix].m : '';
	};

	SetPlayer.prototype.altyaziGecis = function () {
		if (this.altSec === null && this.altlar.length) { this.altyaziSec(this.altlar[0].lang); }
		else { this.altyaziSec(null); }
	};

	/**
	 * Alt menüye DOĞRUDAN gider — kontrol çubuğundaki kısayol düğmeleri için.
	 */
	/** Kontrol çubuğundaki kısayol düğmeleri — hepsi `menuAc` üzerinden. */
	SetPlayer.prototype.menuGit = function (yer) { this.menuAc(yer); };

	/**
	 * Altyazı ve dil düğmelerinin görünürlüğü ve durumu.
	 *
	 * İçeriğin desteklemediği düğmeyi göstermek, tıklayınca boş bir menü açan
	 * ölü bir kontrol demekti. Eski tek sesli kayıtlarda dil düğmesi, altyazısı
	 * olmayanlarda altyazı düğmesi hiç görünmüyor.
	 *
	 * Altyazı düğmesi ETKİN altyazı varken vurgulanıyor — zorunlu altyazı da
	 * buna dahil: ekranda metin varken düğmenin sönük durması, izleyiciye
	 * "altyazı kapalı ama yazı çıkıyor" gibi çelişkili bir durum gösterirdi.
	 */
	SetPlayer.prototype.dugmeleriTazele = function () {
		if (!this.dgDil) { return; }

		/**
		 * DİL DÜĞMESİ — EN AZ İKİ SES VARSA.
		 *
		 * Tek sesli içerikte menü açıp tek satır göstermek seçim değil, boş bir
		 * tıklama. Yerli filmlerin çoğu tek sesli.
		 */
		this.dgDil.style.display = (this.sesler.length > 1) ? '' : 'none';

		/**
		 * ALTYAZI DÜĞMESİ — SEÇİLEBİLİR ALTYAZI VARSA.
		 *
		 * `forced` SAYILMIYOR, bilerek. Zorunlu altyazı kullanıcının seçtiği bir
		 * şey değil: seçili sesin dilinde bir zorunlu dosya varsa kendiliğinden
		 * devreye giriyor (yabancı dilde konuşulan sahneler). Menüye koyulacak
		 * bir satırı yok.
		 *
		 * Eskiden `altlar.length || forced.length` yazıyordu ve yalnız zorunlu
		 * altyazısı olan bir içerikte düğme çıkıyor, açılan menüde "Kapalı"
		 * dışında hiçbir seçenek olmuyordu.
		 */
		this.dgAltyazi.style.display = this.altlar.length ? '' : 'none';

		this.dgAltyazi.classList.toggle('sp-dugme--acik', this.altSec !== null);
	};

	/** Kullanıcı ayarlarını katmana uygular. */
	SetPlayer.prototype.altyaziStil = function () {
		var a = this.altyazi, s = this.altyaziMetin.style;

		s.fontSize   = (a.boyut / 100 * 1.6) + 'em';
		s.color      = a.renk;
		s.background = a.arka > 0 ? 'rgba(0,0,0,' + (a.arka / 100) + ')' : 'transparent';
		s.textShadow = a.kenar ? '0 2px 4px rgba(0,0,0,.95), 0 0 3px rgba(0,0,0,.9)' : 'none';

		this.altyaziKatman.style.bottom = (4.5 + (a.konum / 10)) + 'em';
	};

	// -------------------------------------------------------------------------
	// Önizleme kareleri
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// Geliştirici araçları koruması
	// -------------------------------------------------------------------------

	/**
	 * OYNATICIYI GELİŞTİRİCİ ARAÇLARI KORUMASINA KAYDEDER.
	 *
	 * BURADAKİ 80 SATIRLIK DÖNGÜ KALDIRILDI — İŞİ `guard.js` DEVRALDI.
	 *
	 * Eski kod doğru fikirdi ama YANLIŞ YERDEYDİ ve iki ayrı biçimde geç
	 * kalıyordu:
	 *
	 *   1. GEÇ BAŞLIYORDU. Burası oynatıcının yapıcısı, yani `<body>`nin sonu.
	 *      O ana kadar `<head>` içindeki `preconnect` etiketleri CDN alan
	 *      adlarını sayfa kaynağına yazmış, `preload` manifest isteğini
	 *      başlatmıştı. Koruma tetiklendiğinde saklamak istediğimiz her şey
	 *      zaten ağ sekmesindeydi.
	 *
	 *   2. GEÇ KARAR VERİYORDU. Saniyede bir nabız, dört sessiz tur — en iyi
	 *      ihtimalle DÖRT SANİYE. Ağ sekmesi açık bekleyen biri için dört
	 *      saniye, manifest adresini ve onlarca segment adresini okumaya
	 *      fazlasıyla yeter.
	 *
	 * `guard.js` aynı ölçümü `<head>`in en başında, akışla ilgili tek bir
	 * istek bile çıkmadan yapıyor ve oynatıcı önyüklemesi onun temiz kararını
	 * bekliyor (`SPG.hazir`). Yani araçlar açıkken artık "yakalanacak" bir
	 * istek oluşmuyor.
	 *
	 * GERİYE KALAN İŞ: kendini tanıtmak. Koruma tetiklendiğinde bu nesne
	 * üzerinden `hls` yok ediliyor ve `video`nun kaynağı sökülüyor —
	 * duraklatılmış video tamponunu doldurmaya, yani segment adreslerini ağ
	 * sekmesine yazmaya devam ettiği için yalnız `pause()` yetmiyor.
	 *
	 * `SPG` YOKSA SESSİZ GEÇİLİYOR. Oynatıcı, korumayı hiç basmayan bir
	 * sayfada da (panel önizlemesi, geliştirme kopyası) çalışmaya devam etmeli.
	 */
	SetPlayer.prototype.devKoru = function () {
		if (window.SPG && typeof window.SPG.kayit === 'function') {
			window.SPG.kayit(this);
		}
	};

	SetPlayer.prototype.onizlemeYukle = function (url) {
		var self = this;

		fetch(url, { credentials: 'omit' })
			.then(function (r) { return r.ok ? r.text() : null; })
			.then(function (metin) {
				if (metin === null) { return; }

				var cue = vttAyristir(metin);
				var kok = url.replace(/[^\/]*$/, '');

				for (var i = 0; i < cue.length; i++) {
					var m = /^(\S+?)(?:#xywh=(\d+),(\d+),(\d+),(\d+))?$/.exec(cue[i].m.trim());
					if (!m) { continue; }

					cue[i].img = /^https?:/i.test(m[1]) ? m[1] : (kok + m[1]);
					cue[i].x = +(m[2] || 0); cue[i].y = +(m[3] || 0);
					cue[i].w = +(m[4] || 0); cue[i].h = +(m[5] || 0);
				}

				self.onizleme = cue;
			})
			.catch(function () {});
	};

	SetPlayer.prototype.onizlemeCiz = function (t) {
		if (!this.onizleme || !this.onizleme.length) { return; }

		/**
		 * En yakın kare aranıyor, tam eşleşme değil.
		 *
		 * Cue aralıkları bitişik olmayabiliyor (son karenin bitişi videonun
		 * sonuyla birebir tutmuyor) ve aradaki boşluğa denk gelen her fare
		 * hareketinde kutu boş kalıyordu.
		 */
		var c = null;

		for (var i = 0; i < this.onizleme.length; i++) {
			if (this.onizleme[i].b <= t) { c = this.onizleme[i]; } else { break; }
		}
		if (!c) { c = this.onizleme[0]; }

		if (!c.img || !c.w || !c.h) { return; }

		var kutu = this.onizlemeKare.getBoundingClientRect();
		if (!kutu.width) { return; }

		var o = kutu.width / c.w;

		var s = this.onizlemeIc.style;
		s.width              = c.w + 'px';
		s.height             = c.h + 'px';
		s.backgroundImage    = 'url("' + c.img + '")';
		s.backgroundPosition = (-c.x) + 'px ' + (-c.y) + 'px';
		s.transform          = 'scale(' + o + ')';

		// Kutunun yüksekliği karenin oranını izlesin — sabit oran varsaymıyoruz.
		this.onizlemeKare.style.height = (kutu.width * (c.h / c.w)) + 'px';
	};

	// -------------------------------------------------------------------------
	// Menü
	// -------------------------------------------------------------------------

	/**
	 * MENÜ AÇMA — TEK GİRİŞ NOKTASI.
	 *
	 * Üç düğme aynı paneli paylaşıyor (ayarlar, ses dili, altyazı) ve davranış
	 * hepsinde aynı olmalı:
	 *
	 *   aynı düğmeye tekrar bas   → kapanır
	 *   başka düğmeye bas         → o panele GEÇER (öncekini kapatmaya gerek yok)
	 *
	 * Eskiden dişli ayrı bir yoldan açılıyordu ve "ayarlar açıkken dil düğmesine
	 * basmak" ile "dil açıkken dişliye basmak" farklı sonuç veriyordu: ilki
	 * geçiyor, ikincisi menüyü kapatıyordu. Aynı görünen iki düğmenin farklı
	 * davranması, kullanıcının hangi düğmenin ne yapacağını öğrenmesini
	 * imkânsız kılıyor.
	 *
	 * @param {string} yer '' (ayarlar kökü) · 'ses' · 'alt'
	 */
	SetPlayer.prototype.menuAc = function (yer) {
		var acik = this.menuKutu.classList.contains('sp-menu--acik');

		/**
		 * AÇIK PANEL `menuYer` İLE TUTULUYOR, `menuYol[0]` İLE DEĞİL.
		 *
		 * Ayarlar kökü için `menuYol` boş dizi, yani `menuYol[0]` `undefined`
		 * oluyor ve `undefined === ''` yanlış dönüyordu: dişliye ikinci kez
		 * basmak menüyü kapatmıyor, yeniden çiziyordu. Dil ve altyazıda sorun
		 * görünmüyordu çünkü onların yolu dolu.
		 *
		 * Dizinin ilk elemanına bakmak zaten dolaylı bir yoldu; açık panelin
		 * kimliği ayrı bir alanda duruyor.
		 */
		if (acik && this.menuYer === yer) { this.menuKapat(); return; }

		this.menuYer = yer;
		this.menuYol = (yer === '') ? [] : [yer];
		this.menuCiz();

		this.menuKutu.classList.add('sp-menu--acik');
		this.kok.classList.add('sp--menuacik');

		this.menuDugmeleri(yer);
	};

	/**
	 * Açık panele ait düğmeyi işaretler, diğerlerini bırakır.
	 *
	 * Dişlide çalışan vurgu (renk + alt nokta) dil ve altyazı düğmelerinde
	 * yoktu; menü açıkken hangi düğmeden geldiği görünmüyordu.
	 */
	SetPlayer.prototype.menuDugmeleri = function (yer) {
		this.dgMenu.classList.toggle('sp-dugme--menuacik', yer === '');
		if (this.dgDil)     { this.dgDil.classList.toggle('sp-dugme--menuacik', yer === 'ses'); }
		if (this.dgAltyazi) { this.dgAltyazi.classList.toggle('sp-dugme--menuacik', yer === 'alt'); }
	};

	SetPlayer.prototype.menuAcKapat = function () { this.menuAc(''); };

	SetPlayer.prototype.menuKapat = function () {
		this.menuKutu.classList.remove('sp-menu--acik');
		this.kok.classList.remove('sp--menuacik');
		this.menuYer = null;
		this.menuDugmeleri(null);
	};

	SetPlayer.prototype.menuTazele = function () {
		if (this.menuKutu.classList.contains('sp-menu--acik')) { this.menuCiz(); }
	};

	SetPlayer.prototype.menuCiz = function () {
		var self = this;
		var yer  = this.menuYol.length ? this.menuYol[this.menuYol.length - 1] : '';

		this.menuKutu.innerHTML = '';

		/**
		 * Panel başlığı.
		 *
		 * GERİ OKU YALNIZ GERÇEKTEN GERİ GİDİLECEK YER VARSA.
		 *
		 * "Ses dili" ve "Altyazı" kendi düğmelerinden açılıyor, yani üstlerinde
		 * bir menü yok. Onlara geri oku koymak, tıklayınca kullanıcıyı hiç
		 * bulunmadığı bir yere (Ayarlar) götürüyordu. Başlık orada düz bir
		 * etiket — tıklanamaz.
		 */
		function baslik(metin, geriVar) {
			if (!geriVar) {
				self.menuKutu.appendChild(el('div', 'sp-menu-ust', metin));
				return;
			}

			var b = el('div', 'sp-menu-baslik', IK.geri + '<span>' + metin + '</span>');
			b.onclick = function (e) { e.stopPropagation(); self.menuYol.pop(); self.menuCiz(); };
			self.menuKutu.appendChild(b);
		}

		function satir(etiket, deger, secili, tik, okVar) {
			var s = el('div', 'sp-menu-satir' + (secili ? ' sp-menu-satir--secili' : ''));

			// Alt menüye götüren satırlarda tik yerine ok var.
			if (!okVar) { s.appendChild(el('span', 'sp-menu-tik', secili ? IK.tik : '')); }

			s.appendChild(el('span', 'sp-menu-etiket', etiket));
			if (deger !== undefined && deger !== null) { s.appendChild(el('span', 'sp-menu-deger', deger)); }

			if (okVar) {
				var ok = el('span', 'sp-menu-tik', IK.ok);
				ok.firstChild.setAttribute('class', 'sp-menu-ok');
				s.appendChild(ok);
			}

			s.onclick = function (e) { e.stopPropagation(); tik(); };
			self.menuKutu.appendChild(s);
			return s;
		}

		// --- ana menü (DİŞLİ) ---
		/**
		 * Dişli YALNIZ kalite ve hız taşıyor.
		 *
		 * Dil ve altyazının kontrol çubuğunda kendi düğmeleri var; aynı şeyi
		 * iki yerden sunmak menüyü şişiriyor ve "hangisinden yapmalıyım"
		 * sorusunu doğuruyordu.
		 */
		if (yer === '') {
			satir('Kalite', (this.kaliteIx === -1 ? 'Otomatik' : this.kalite), false, function () { self.menuYol.push('kalite'); self.menuCiz(); }, true);
			satir('Hız', (this.video.playbackRate === 1 ? 'Normal' : this.video.playbackRate + 'x'), false,
			      function () { self.menuYol.push('hiz'); self.menuCiz(); }, true);
			return;
		}

		// --- dil ---
		if (yer === 'ses') {
			baslik('Sesler');
			this.sesler.forEach(function (s) {
				satir(s.ad, null, s.ix === self.sesIx, function () { self.sesSec(s.ix); });
			});
			return;
		}

		// --- altyazı + ayarları (TEK PANEL) ---
		/**
		 * Altyazı listesi ve görünüm ayarları AYNI panelde.
		 *
		 * İkisi ayrı menüdeyken kullanıcı boyutu değiştirmek için altyazıyı
		 * kapatıp geri açıyor, sonucu görmek için menüyü kapatıyor ve baştan
		 * açıyordu. Aynı panelde ayar anında ekranda görünüyor.
		 */
		if (yer === 'alt') {
			baslik('Altyazılar');

			satir('Kapalı', null, this.altSec === null, function () { self.altyaziSec(null); });

			this.altlar.forEach(function (p) {
				satir(p.label, null, self.altSec === p.lang, function () { self.altyaziSec(p.lang); });
			});

			/**
			 * GÖRÜNÜM VARSAYILAN OLARAK KAPALI.
			 *
			 * Üç kaydırıcı, altı renk kutusu ve iki satır açık dururken dil
			 * listesi panelin tepesinde kayboluyordu — oysa asıl iş o.
			 * Ayarlar bir kez yapılıp bir daha dokunulmayan şeyler; açılır
			 * bölüm olarak durmaları doğru.
			 */
			var bolum = el('div', 'sp-menu-satir sp-menu-katla');
			bolum.appendChild(el('span', 'sp-menu-etiket', 'Altyazı Ayarları'));

			var okKutu = el('span', 'sp-menu-tik', IK.ok);
			okKutu.firstChild.setAttribute('class', 'sp-menu-ok');
			bolum.appendChild(okKutu);

			bolum.classList.toggle('sp-menu-katla--acik', !!this.gorunumAcik);

			bolum.onclick = function (e) {
				e.stopPropagation();
				self.gorunumAcik = !self.gorunumAcik;
				self.menuCiz();
			};

			this.menuKutu.appendChild(bolum);

			if (this.gorunumAcik) { this.altyaziAyarCiz(); }
			return;
		}

		// --- kalite ---
		if (yer === 'kalite') {
			baslik('Kalite', true);

			/**
			 * HLS MANTIĞI: "Otomatik" HER ZAMAN İLK SIRADA.
			 *
			 * Gerçek çok kaliteli içerikte otomatik seçim bant genişliğine göre
			 * seviye değiştiriyor; tek kaliteli içerikte değiştirecek bir şey
			 * yok ama menü aynı görünüyor. İzleyici için ayrım yok — ve
			 * olmamalı: "bu videoda kalite seçilemiyor" diye bir şey öğrenmesi
			 * gerekmiyor.
			 *
			 * Otomatik seçiliyken o an oynayan seviye parantez içinde yazıyor
			 * (`Otomatik (1080p)`), tıpkı diğer HLS oynatıcılarında olduğu gibi.
			 */
			satir('Otomatik', self.oynayanKalite(), self.kaliteIx === -1, function () {
				self.kaliteSec(-1);
				self.menuYol.pop();
				self.menuCiz();
			});

			this.kaliteListesi().forEach(function (k) {
				satir(k.ad, null, self.kaliteIx === k.ix, function () {
					self.kaliteSec(k.ix);
					self.menuYol.pop();
					self.menuCiz();
				});
			});
			return;
		}

		// --- hız ---
		if (yer === 'hiz') {
			baslik('Hız', true);
			[0.5, 0.75, 1, 1.25, 1.5, 2].forEach(function (h) {
				satir(h === 1 ? 'Normal' : h + 'x', null, self.video.playbackRate === h, function () {
					self.video.playbackRate = h;
					self.menuYol.pop();
					self.menuCiz();
				});
			});
		}
	};

	/**
	 * Kalite listesi — GERÇEK seviyeler varsa onlar, yoksa sahteler.
	 *
	 * Eski çok kaliteli kayıtlarda hls.js gerçek seviyeler buluyor ve seçim
	 * gerçekten akışı değiştiriyor. Yeni tek kaliteli düzende değiştirilecek
	 * bir şey yok; liste yine görünüyor ama seçim yalnız etikette kalıyor.
	 *
	 * Bu ayrımın izleyiciye YANSIMAMASI bilinçli: menünün bazı videolarda
	 * kaybolması, "bu video bozuk mu" sorusunu doğuruyordu.
	 */
	SetPlayer.prototype.kaliteListesi = function () {
		var g = this.gercekSeviyeler();

		if (g.length > 1) {
			/**
			 * YÜKSEKTEN ALÇAĞA SIRALANIYOR.
			 *
			 * hls.js seviyeleri bit hızına göre ARTAN sırada veriyor; olduğu
			 * gibi basınca menüde "720p, 1080p" çıkıyordu. Yeni içerikte sahte
			 * liste zaten "1080p, 720p" olduğu için aynı oynatıcı iki farklı
			 * sıra gösteriyordu — izleyici için tutarsızlık.
			 *
			 * `ix` GERÇEK seviye indeksi olarak taşınıyor: görüntüdeki sıra
			 * değişse de `hls.currentLevel`'a doğru numara gitmeli.
			 */
			return g
				.map(function (s, i) {
					return {
						ad: s.height ? s.height + 'p' : Math.round((s.bitrate || 0) / 1000) + 'k',
						ix: i,
						olcu: s.height || s.bitrate || 0
					};
				})
				.sort(function (a, b) { return b.olcu - a.olcu; });
		}

		return this.kaliteler.map(function (ad, i) { return { ad: ad, ix: i, olcu: 0 }; });
	};

	/** hls.js'in bulduğu seviyeler; yerli oynatmada ya da tek varyantta boş. */
	SetPlayer.prototype.gercekSeviyeler = function () {
		return (this.hls && this.hls.levels && this.hls.levels.length) ? this.hls.levels : [];
	};

	/**
	 * Otomatik seçiliyken o an oynayan seviyenin adı — parantez içinde gösterilir.
	 *
	 * Gerçek seviye yoksa listenin ilkini gösteriyor: sahte kalitede de
	 * "Otomatik (1080p)" yazması, menünün gerçek bir oynatıcı gibi davranmasını
	 * sağlıyor.
	 */
	SetPlayer.prototype.oynayanKalite = function () {
		var g = this.gercekSeviyeler();

		if (g.length > 1 && this.hls) {
			var ix = this.hls.currentLevel;
			if (ix >= 0 && g[ix]) { return g[ix].height ? g[ix].height + 'p' : null; }
			return null;
		}

		var l = this.kaliteListesi();
		return l.length ? l[0].ad : null;
	};

	/**
	 * Kalite seçer.
	 *
	 * @param {number} ix -1 = otomatik
	 */
	SetPlayer.prototype.kaliteSec = function (ix) {
		this.kaliteIx = ix;

		if (this.hls && this.gercekSeviyeler().length > 1) { this.hls.currentLevel = ix; }

		/**
		 * Ad, GERÇEK indekse göre aranıyor — menüdeki sıraya göre değil.
		 * Liste yüksekten alçağa gösterildiği için ikisi artık aynı değil.
		 */
		this.kalite = 'Otomatik';

		if (ix !== -1) {
			var l = this.kaliteListesi();
			for (var i = 0; i < l.length; i++) { if (l[i].ix === ix) { this.kalite = l[i].ad; break; } }
		}

		this.bilgi(ix === -1 ? 'Otomatik kalite' : this.kalite);
	};

	SetPlayer.prototype.altSecAd = function () {
		if (this.altSec === null) { return 'Kapalı'; }
		for (var i = 0; i < this.altlar.length; i++) {
			if (this.altlar[i].lang === this.altSec) { return this.altlar[i].label; }
		}
		return 'Kapalı';
	};

	SetPlayer.prototype.altyaziAyarCiz = function () {
		var self = this, a = this.altyazi;

		function kaydirici(etiket, anahtar, min, max, adim, birim) {
			var kutu = el('div', 'sp-menu-alan');

			var et = el('label');
			var ad = el('span', null, etiket);
			var dg = el('b');
			et.appendChild(ad);
			et.appendChild(dg);
			kutu.appendChild(et);

			var g = document.createElement('input');
			g.type = 'range'; g.min = min; g.max = max; g.step = adim; g.value = a[anahtar];

			/**
			 * DOLU KISIM GRADYANLA ÇİZİLİYOR.
			 *
			 * `-webkit-appearance:none` verildiği anda tarayıcının kendi dolgusu
			 * (ve `accent-color`) kayboluyor; ray düz gri kalıyor ve kullanıcı
			 * değeri yalnız tutamağın yerinden tahmin ediyordu. Ne kadarının
			 * seçili olduğu görünmeli.
			 */
			function boya() {
				var o = (g.value - min) / (max - min) * 100;
				g.style.background =
					'linear-gradient(90deg, var(--sp-vurgu) 0%, var(--sp-vurgu) ' + o + '%,'
					+ ' rgba(255,255,255,.2) ' + o + '%, rgba(255,255,255,.2) 100%)';
				dg.textContent = a[anahtar] + (birim || '');
			}

			boya();

			g.oninput = function () {
				a[anahtar] = +g.value;
				boya();
				self.altyaziStil();
				ayarYaz(a);
			};

			g.onclick = function (e) { e.stopPropagation(); };
			kutu.appendChild(g);
			self.menuKutu.appendChild(kutu);
		}

		kaydirici('Boyut', 'boyut', 60, 200, 10, '%');
		kaydirici('Arka plan', 'arka', 0, 100, 10, '%');
		kaydirici('Yükseklik', 'konum', 0, 60, 5, '');

		var renkKutu = el('div', 'sp-menu-alan');
		renkKutu.appendChild(el('label', null, 'Renk'));

		var sira = el('div', 'sp-renkler');
		['#ffffff', '#ffe600', '#00e5ff', '#7cff6b', '#ff8a3d', '#ff6b6b'].forEach(function (r) {
			var d = el('button', 'sp-renk' + (a.renk === r ? ' sp-renk--secili' : ''));
			d.style.background = r;
			d.onclick = function (e) {
				e.stopPropagation();
				a.renk = r; ayarYaz(a); self.altyaziStil(); self.menuCiz();
			};
			sira.appendChild(d);
		});

		renkKutu.appendChild(sira);
		this.menuKutu.appendChild(renkKutu);

		var kenar = el('div', 'sp-menu-satir');
		kenar.appendChild(el('span', 'sp-menu-tik', a.kenar ? IK.tik : ''));
		kenar.appendChild(el('span', 'sp-menu-etiket', 'Kenar gölgesi'));
		kenar.onclick = function (e) {
			e.stopPropagation();
			a.kenar = !a.kenar; ayarYaz(a); self.altyaziStil(); self.menuCiz();
		};
		this.menuKutu.appendChild(kenar);

		var sifirla = el('div', 'sp-menu-satir');
		sifirla.appendChild(el('span', 'sp-menu-tik', ''));
		sifirla.appendChild(el('span', 'sp-menu-etiket', 'Varsayılana dön'));
		sifirla.onclick = function (e) {
			e.stopPropagation();
			self.altyazi = JSON.parse(JSON.stringify(VARSAYILAN));
			ayarYaz(self.altyazi); self.altyaziStil(); self.menuCiz();
		};
		this.menuKutu.appendChild(sifirla);
	};

	// =========================================================================
	// Reklam (VAST)
	// =========================================================================

	/**
	 * Reklam katmanını kurar ve etiketleri hazırlar.
	 *
	 * REKLAM AYRI BİR `<video>` ELEMANINDA OYNUYOR.
	 *
	 * İçerik videosunun `src`'sini değiştirmek hls.js'i söküp yeniden kurmayı
	 * gerektiriyor: tampon boşalıyor, ses parçası seçimi sıfırlanıyor ve
	 * reklamdan dönünce izleyici baştan bekliyor. Ayrı eleman bunların hiçbirine
	 * dokunmuyor — içerik arka planda duraklamış hâlde tamponunu koruyor.
	 */
	SetPlayer.prototype.reklamKur = function () {
		var r = this.ayar.reklam;
		if (!r || (!r.tag && !r.tag2)) { return; }

		var self = this;

		this.reklamKatman = el('div', 'sp-reklam');

		this.reklamVideo = document.createElement('video');
		this.reklamVideo.playsInline = true;
		this.reklamVideo.setAttribute('playsinline', '');
		this.reklamVideo.setAttribute('webkit-playsinline', '');
		this.reklamKatman.appendChild(this.reklamVideo);

		var alt = el('div', 'sp-reklam-alt');
		this.reklamEtiket = el('span', 'sp-reklam-etiket', r.mesaj || 'Reklam');
		this.reklamAtla   = el('button', 'sp-reklam-atla', 'Reklamı geç');
		this.reklamAtla.style.display = 'none';
		alt.appendChild(this.reklamEtiket);
		alt.appendChild(this.reklamAtla);
		this.reklamKatman.appendChild(alt);

		this.kok.appendChild(this.reklamKatman);

		/**
		 * Reklamın üstüne tıklamak hedefe götürüyor — YENİ SEKMEDE.
		 *
		 * Aynı sekmede açmak izleyiciyi videodan koparıyor ve geri döndüğünde
		 * oynatma baştan başlıyor.
		 */
		this.reklamVideo.addEventListener('click', function () {
			if (!self.reklamAktif || !self.reklamAktif.tikla) { return; }

			pikselAt(self.reklamAktif.izleme.click);
			window.open(self.reklamAktif.tikla, '_blank', 'noopener');
		});

		this.reklamAtla.onclick = function () { self.reklamBitir('skip'); };

		/**
		 * ORTA REKLAM %50'de.
		 *
		 * Panelde ikinci etiket "orta reklam" olarak duruyordu ama zamanı
		 * ayarlanabilir değildi; içeriğin ortası hem tahmin edilebilir hem de
		 * izleyicinin en az rahatsız olduğu nokta.
		 */
		if (r.tag2) {
			this.video.addEventListener('timeupdate', function () {
				if (self.ortaGosterildi || !self.video.duration) { return; }
				if (self.video.currentTime < self.video.duration / 2) { return; }

				self.ortaGosterildi = true;
				self.reklamGoster(r.tag2);
			});
		}
	};

	/**
	 * Etiketi çeker, çözer ve oynatır.
	 *
	 * HER HATA İÇERİĞE DÖNÜYOR. Reklam alınamadı, bozuk geldi, medya dosyası
	 * oynatılamadı — hepsinde sonuç aynı: reklam atlanır, video oynar. Reklam
	 * yüzünden içeriğin açılmaması kabul edilebilir bir sonuç değil.
	 */
	SetPlayer.prototype.reklamGoster = function (tag, derinlik) {
		var self = this;
		derinlik = derinlik || 0;

		if (derinlik > 3) { return this.reklamBitir('error'); }

		fetch(tag, { credentials: 'omit' })
			.then(function (r) { return r.ok ? r.text() : null; })
			.then(function (xml) {
				if (!xml) { throw new Error('bos'); }

				var d = xmlCoz(xml);
				if (!d) { throw new Error('xml'); }

				// Wrapper — bir üst etikete yönlendiriyor.
				var sarmal = xmlMetin(d, 'VASTAdTagURI');
				if (sarmal) { return self.reklamGoster(sarmal, derinlik + 1); }

				var medya = vastMedya(d, self.reklamVideo);
				if (!medya) { throw new Error('medya'); }

				var sure = vastSure(xmlMetin(d, 'Duration'));

				var linear = d.getElementsByTagName('Linear')[0];
				var atlaOf = linear ? linear.getAttribute('skipoffset') : null;

				var ayarAtla = (self.ayar.reklam && self.ayar.reklam.atla > 0)
					? self.ayar.reklam.atla : -1;

				var izleme = {
					impression: (function () {
						var o = [], n = d.getElementsByTagName('Impression');
						for (var i = 0; i < n.length; i++) {
							var u = (n[i].textContent || '').trim();
							if (u) { o.push(u); }
						}
						return o;
					}()),
					start:    vastIzleme(d, 'start'),
					q1:       vastIzleme(d, 'firstQuartile'),
					mid:      vastIzleme(d, 'midpoint'),
					q3:       vastIzleme(d, 'thirdQuartile'),
					complete: vastIzleme(d, 'complete'),
					skip:     vastIzleme(d, 'skip'),
					click:    (function () {
						var o = [], n = d.getElementsByTagName('ClickTracking');
						for (var i = 0; i < n.length; i++) {
							var u = (n[i].textContent || '').trim();
							if (u) { o.push(u); }
						}
						return o;
					}())
				};

				self.reklamOynat({
					media:  medya,
					sure:   sure,
					atla:   (atlaOf !== null && atlaOf !== '') ? vastAtla(atlaOf, sure) : ayarAtla,
					tikla:  xmlMetin(d, 'ClickThrough'),
					izleme: izleme
				});
			})
			.catch(function () { self.reklamBitir('error'); });
	};

	SetPlayer.prototype.reklamOynat = function (ad) {
		var self = this;

		this.reklamAktif = ad;
		this.video.pause();

		this.kok.classList.add('sp--reklam');
		this.reklamAtla.style.display = 'none';

		var isaret = { q1: false, mid: false, q3: false, basladi: false };

		this.reklamVideo.src = ad.media;

		this.reklamVideo.onloadedmetadata = function () {
			if (!ad.sure || !isFinite(ad.sure)) { ad.sure = self.reklamVideo.duration || 0; }
		};

		this.reklamVideo.ontimeupdate = function () {
			var v = self.reklamVideo;
			var t = v.currentTime, s = v.duration || ad.sure || 0;

			if (!isaret.basladi && t > 0) { isaret.basladi = true; pikselAt(ad.izleme.start); }

			if (s > 0) {
				if (!isaret.q1  && t >= s * 0.25) { isaret.q1  = true; pikselAt(ad.izleme.q1); }
				if (!isaret.mid && t >= s * 0.50) { isaret.mid = true; pikselAt(ad.izleme.mid); }
				if (!isaret.q3  && t >= s * 0.75) { isaret.q3  = true; pikselAt(ad.izleme.q3); }
			}

			// Etiket kalan süreyi gösteriyor — izleyici ne kadar bekleyeceğini bilsin.
			var kalan = Math.max(0, Math.ceil((s || 0) - t));
			self.reklamEtiket.textContent =
				((self.ayar.reklam && self.ayar.reklam.mesaj) || 'Reklam') + (kalan ? ' · ' + kalan : '');

			if (ad.atla >= 0) {
				if (t >= ad.atla) { self.reklamAtla.style.display = ''; self.reklamAtla.textContent = 'Reklamı geç'; }
				else { self.reklamAtla.style.display = ''; self.reklamAtla.textContent = Math.ceil(ad.atla - t) + ' sn sonra geçilebilir'; self.reklamAtla.disabled = true; }

				self.reklamAtla.disabled = (t < ad.atla);
			}
		};

		this.reklamVideo.onended = function () { self.reklamBitir('complete'); };
		this.reklamVideo.onerror = function () { self.reklamBitir('error'); };

		pikselAt(ad.izleme.impression);

		var p = this.reklamVideo.play();
		if (p && p.catch) {
			/**
			 * OTOMATİK OYNATMA REDDEDİLİRSE SESSİZ DENENİYOR.
			 *
			 * Tarayıcılar sesli otomatik oynatmayı engelliyor. Reklamı hiç
			 * göstermemektense sessiz göstermek reklam vereni de izleyiciyi de
			 * daha az mağdur ediyor; ikinci deneme de başarısızsa içeriğe
			 * geçiliyor.
			 */
			p.catch(function () {
				self.reklamVideo.muted = true;
				var p2 = self.reklamVideo.play();
				if (p2 && p2.catch) { p2.catch(function () { self.reklamBitir('error'); }); }
			});
		}
	};

	SetPlayer.prototype.reklamBitir = function (sebep) {
		var ad = this.reklamAktif;

		if (ad) {
			if (sebep === 'skip')     { pikselAt(ad.izleme.skip); }
			if (sebep === 'complete') { pikselAt(ad.izleme.complete); }
		}

		this.reklamAktif = null;
		this.kok.classList.remove('sp--reklam');

		if (this.reklamVideo) {
			this.reklamVideo.ontimeupdate = null;
			this.reklamVideo.onended = null;
			this.reklamVideo.onerror = null;
			try { this.reklamVideo.pause(); } catch (e) {}
			this.reklamVideo.removeAttribute('src');
			try { this.reklamVideo.load(); } catch (e) {}
		}

		var p = this.video.play();
		if (p && p.catch) { p.catch(function () {}); }
	};

	// -------------------------------------------------------------------------
	// Genel eylemler
	// -------------------------------------------------------------------------

	SetPlayer.prototype.cevir = function () {
		if (this.video.paused) { var p = this.video.play(); if (p && p.catch) { p.catch(function () {}); } }
		else { this.video.pause(); }
	};

	SetPlayer.prototype.atla = function (s) {
		this.video.currentTime = Math.max(0, Math.min((this.video.duration || 0), this.video.currentTime + s));
		this.bilgi((s > 0 ? '+' : '') + s + ' sn');
	};

	SetPlayer.prototype.sessizCevir = function () { this.video.muted = !this.video.muted; };

	/**
	 * WEBKIT DALI ATLANMAMALI.
	 *
	 * Yalnız standart API denenip doğrudan `video.webkitEnterFullscreen()`'e
	 * düşülürse Safari 16.4 öncesinde YEREL video tam ekranı açılıyor. O mod
	 * üst pencereye `fullscreenchange` YANSITMIYOR ve temalardaki izlenme
	 * geçidi (reklamı belirli süre sonra açan sayaç) tam ekranı tetik olarak
	 * kullandığı için hiç başlamıyor. Elemana `webkitRequestFullscreen`
	 * uygulanınca çerçeve gerçek tam ekrana geçiyor ve olay zinciri korunuyor.
	 */
	SetPlayer.prototype.tamEkran = function () {
		if (document.fullscreenElement || document.webkitFullscreenElement) {
			if (document.exitFullscreen) { document.exitFullscreen(); }
			else if (document.webkitExitFullscreen) { document.webkitExitFullscreen(); }
		}
		else if (this.kok.requestFullscreen) { this.kok.requestFullscreen(); }
		else if (this.kok.webkitRequestFullscreen) { this.kok.webkitRequestFullscreen(); }
		else if (this.video.webkitEnterFullscreen) { this.video.webkitEnterFullscreen(); }
	};

	SetPlayer.prototype.bilgi = function (metin) {
		var self = this;
		this.bilgiKutu.textContent = metin;
		this.bilgiKutu.classList.add('sp-bilgi--acik');
		clearTimeout(this._bilgi);
		this._bilgi = setTimeout(function () { self.bilgiKutu.classList.remove('sp-bilgi--acik'); }, 700);
	};

	/**
	 * HATA KUTUSU — DÖNEN ÇARKI DA DURDURUYOR.
	 *
	 * Eskiden yalnız metni yazıp `sp--hata` ekliyordu; `sp--yukleniyor` sınıfı
	 * üstünde kalıyordu ve çark hata yazısının arkasında dönmeye devam ediyordu.
	 * Televizyonda görünen tabloyu tam olarak bu üretiyordu: "çark dönüyor,
	 * video başlamıyor" — oysa oynatıcı çoktan pes etmişti.
	 *
	 * KOD METNE EKLENİYOR. Televizyonda geliştirici aracı yok; ekranda görünen
	 * kısa kod (`manifestLoadError 404`, `yerli-takildi`…) hangi katmanın
	 * çöktüğünü söyleyen tek kanal.
	 */
	SetPlayer.prototype.hataGoster = function (metin, kod) {
		this.bekciDur();

		this.hataKutu.textContent = kod ? (metin + ' (' + kod + ')') : metin;
		this.kok.classList.remove('sp--yukleniyor');
		this.kok.classList.add('sp--hata');
	};

	/**
	 * SES SEVİYESİ TARAYICIDA SAKLANIYOR.
	 *
	 * Her sayfa açılışında tam sese dönüyordu: sesi kısan izleyici, bir sonraki
	 * videoda aynı işi yeniden yapmak zorunda kalıyordu. Konum ve altyazı
	 * ayarları zaten saklanıyor; ses seviyesinin saklanmaması tutarsızlıktı.
	 *
	 * Kısma ayrı tutuluyor: sesi kapatmak "0 seviye" demek değil, geri açınca
	 * eski seviyeye dönmeli.
	 */
	SetPlayer.prototype.sesKaydet = function () {
		try {
			localStorage.setItem('setplayer.ses', JSON.stringify({
				s: Math.round(this.video.volume * 100),
				k: this.video.muted ? 1 : 0
			}));
		} catch (e) {}
	};

	/**
	 * SES KAYDIRICISI MOBİLDE GİZLENİYOR.
	 *
	 * iOS ve çoğu Android tarayıcısı `video.volume` yazmasını yok sayıyor: ses
	 * seviyesi donanım tuşlarına ait ve web sayfası ona karışamıyor. Kaydırıcı
	 * ekranda duruyor ama sürüklendiğinde hiçbir şey olmuyordu — bozuk bir
	 * kontrol, yer kaplamaktan başka işe yaramayan.
	 *
	 * TESPİT DENEYEREK YAPILIYOR, CİHAZ ADINA BAKARAK DEĞİL: kullanıcı
	 * dizgesine göre karar vermek her yeni tarayıcı sürümünde yeniden
	 * ayarlanması gereken bir liste demek. Değeri yazıp geri okuyoruz; tuttuysa
	 * denetim bizde.
	 *
	 * SESSİZE ALMA DÜĞMESİ KALIYOR: `muted` mobilde de çalışıyor ve otomatik
	 * oynatma sessiz başladığında sesi açmanın tek yolu o.
	 */
	SetPlayer.prototype.sesDenetimi = function () {
		var v = this.video;
		var eski = v.volume;
		var hedef = (eski > 0.55) ? 0.5 : 0.75;

		try { v.volume = hedef; } catch (e) {}

		var yazilabilir = (Math.abs(v.volume - hedef) < 0.01);

		try { v.volume = eski; } catch (e) {}

		if (!yazilabilir) {
			this.sesRay.style.display = 'none';
			this.kok.classList.add('sp--sessizkilit');
		}

		return yazilabilir;
	};

	SetPlayer.prototype.sesYukle = function () {
		if (!this.sesDenetimi()) { return; }

		var d = null;

		try { d = JSON.parse(localStorage.getItem('setplayer.ses') || 'null'); } catch (e) {}

		if (d) {
			if (typeof d.s === 'number' && d.s >= 0 && d.s <= 100) { this.video.volume = d.s / 100; }
			this.video.muted = !!d.k;
		}

		/**
		 * `volumechange` DEĞER DEĞİŞMEDİĞİNDE TETİKLENMİYOR.
		 *
		 * Kayıtlı seviye 100 ise (varsayılanla aynı) olay hiç gelmiyor ve
		 * kaydırıcı ile ikon ilk çizimde boş kalıyordu.
		 *
		 * KAYIT YOKKEN DE ÇAĞRILIYOR — eskiden `if (!d) return;` ile buradan
		 * erken çıkılıyordu ve İLK ziyaretçide gösterge hiç çizilmiyordu.
		 * Belirti kafa karıştırıcıydı: dolu çubuk CSS'teki `width:100%`
		 * varsayılanıyla doğru görünüyor ama yuvarlak topuzun `left` değeri
		 * hiç yazılmadığı için `auto` kalıyor, yani EN SOLDA duruyordu. Ses
		 * açık, çubuk dolu, topuz sıfırda — üçü birbiriyle çelişiyordu.
		 */
		this.video.dispatchEvent(new Event('volumechange'));
	};

	// --- devam konumu ---

	SetPlayer.prototype.konumAnahtar = function () { return 'setplayer.konum.' + (this.ayar.id || ''); };

	SetPlayer.prototype.konumKaydet = function () {
		if (!this.ayar.resume || !this.ayar.id) { return; }
		if (Date.now() - (this._konumSon || 0) < 5000) { return; }

		this._konumSon = Date.now();
		try { localStorage.setItem(this.konumAnahtar(), String(Math.floor(this.video.currentTime))); } catch (e) {}
	};

	SetPlayer.prototype.konumOku = function () {
		if (!this.ayar.resume || !this.ayar.id) { return 0; }
		try { return parseInt(localStorage.getItem(this.konumAnahtar()) || '0', 10) || 0; } catch (e) { return 0; }
	};

	SetPlayer.prototype.yikil = function () {
		if (this.hls) { this.hls.destroy(); this.hls = null; }
		// Belge düzeyindeki dinleyici kökle birlikte gitmiyor; elle kaldırılmalı.
		if (this.fareHareket) { document.removeEventListener('mousemove', this.fareHareket); }
		if (this.kok && this.kok.parentNode) { this.kok.parentNode.removeChild(this.kok); }
	};

	global.SetPlayer = SetPlayer;

}(window));
