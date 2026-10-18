# ADR-004: Eski Sürüm Uyumluluğu ve Sürüm Artırma Politikası

## Durum
CloudStream istemcisi yüklü eklentileri güncellemek için tamsayı (`integer`) sürüm numarasını (`version`) karşılaştırır. Yeni sürüm numarası mevcut kurulu olandan büyük değilse güncelleme gerçekleşmez.

## Karar
Eski `Kraptor123/cs-kraptor` repository'sinden canlandırılan tüm eklentilerin yeni sürüm numarası, eski repository'deki en yüksek sürüm numarasından **kesinlikle daha büyük** olacaktır:
$$\text{yeniSürüm} > \text{eskiSürüm}$$
Kodda veya parser mantığında yapılan her değişiklikte sürüm numarası en az 1 artırılacaktır.

## Sonuçlar
- Eski kullanıcılar yeni depoyu eklediklerinde eklentileri sorunsuz bir şekilde güncel sürüme geçer.
- Sürüm gerilemesi (version regression) engellenir.
