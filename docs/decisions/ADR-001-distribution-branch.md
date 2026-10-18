# ADR-001: Dağıtım Kanalı Olarak builds Branch Kullanımı

## Durum
CloudStream eklentileri derlendiğinde `.cs3` (DEX içeren zip paketleri) ve `plugins.json` manifesti üretilir. Bu ikili (binary) dosyaların kaynak kodların bulunduğu `main` branch'inde saklanması Git geçmişini şişirmekte, PR diff'lerini kirletmekte ve karmaşıklık yaratmaktadır.

## Karar
Dağıtılabilir tüm artifact'lerin (`*.cs3`, `plugins.json`, `repo.json`) **ayrı ve bağımsız bir `builds` branch'inde** tutulmasına karar verilmiştir.
- `main` branch'i: Yalnızca saf kaynak kodları ve yapılandırmaları içerir.
- `builds` branch'i: GitHub Actions tarafından otomatik ve atomik olarak güncellenir.

## Sonuçlar
- Kaynak kod reposu temiz ve hafif kalır.
- Kullanıcılar sadece stabil `builds` branch'inden artifact indirir.
- Başarısız derlemeler mevcut çalışan yayınları bozmaz.
