# TACZ VR Compat (Vivecraft x TACZ)

Minecraft **Forge 1.20.1** modu. Vivecraft ile VR'da TACZ silahlarını gerçek silah gibi elinde tutmanı sağlar.

## Kontroller (Quest varsayılan tuşları)

| Ne | Nasıl |
|---|---|
| Ateş | Sağ tetik |
| Nişan | Gez-arpacığı / dürbünü gözünün önüne getir |
| Reload | **A** (TACZ'ın normal animasyonlu reload'u, her silahta) |
| Şarjörü elle çıkar | Sol elin boşken silahın şarjörüne götür, **grip** (şarjör düşer, içindeki mermiler envantere döner) |
| Yeni şarjör al | Sol elini beline götür, **grip**'i basılı tut (elinde şarjör belirir) |
| Şarjörü tak | Şarjörü silahın şarjör yuvasına değdir (kendiliğinden oturur) |
| Mermiyi namluya sür | Namlu boşsa: sol elinle gövdenin üstünü (sürgü/kurma kolu) grip ile tut, geri çek, bırak |
| Dipçik / süngü vuruşu | Silahı hızlıca ileri it |
| İki elle tutma | Sol elini el koruyucusuna, namlu hattına yakın getir |
| Aksesuar tak | Aksesuarı (dürbün, susturucu, dipçik...) sol eline al. Silahta gideceği yerde koyu yeşil bir işaret belirir; sol elini oraya ~15 cm yaklaştır, **kendiliğinden takılır** (tuş yok, titreşim ve ses gelir). İşaret kırmızıysa o aksesuar bu silaha uymuyor. Takılı dürbünün yerine yenisini götürürsen değiştirir, eskisi eline gelir |
| Aksesuar çıkar | Sol elin boşken takılı aksesuarın yanına götür (sarı işaret), **grip**'i ~1 saniye basılı tut, sol eline gelir |
| Silahı ver | Silahı (ya da mermi/aksesuarı) arkadaşının eline uzat, **grip** |

**A** her zaman normal reload yapar; elle şarjör değiştirmek isteğe bağlı (şarjörü çıkarabilen silahlarda). Şarjör dışarıdayken A'ya basarsan da TACZ dolu şarjör takar. Elle şarjörü tamamen kapatmak için ayarlardan `manualMagazine = false`.

**Aksesuarı sol ele almak:** Aksesuarı hotbar'dan seç (sağ elinde olsun) ve sol elini omzunun arkasına götür. Vivecraft iki elindekini yer değiştirir. Sonra hotbar'dan silahı seç. Ya da **X** ile envanteri açıp aksesuarı kalkan yuvasına koy.

Sol elinde dürbün, mermi ya da elle tuttuğun bir şarjör varken silah "iki el" moduna geçmez, yani aksesuar takarken silah dönmez.

## Diğer özellikler

- **Elde 3D silah:** Kabza kontrolcüde, namlu kontrolcünün gösterdiği yönde. Reload, ateş ve sürgü animasyonları oynar.
- **Namludan ateş:** Mermi namludan, silahın baktığı yöne çıkar. Gez-arpacık hattı `zeroDistance` mesafesinde (25 blok) mermi yoluyla kesişir.
- **Çalışan dürbün:** Büyüten dürbünlerden bakınca merceğin içinde gerçekten yakınlaşmış görüntü ve nişangah görünür (büyütme dürbünün kendi değeri: 2.5x, 4x, 8x...). Kırmızı nokta ve holografik nişangahlar normal çalışır.
- **Eller silahın üstünde:** Kendi VR görüşünde Vivecraft'ın silahın içinde kalan eli gizlenir; yerine kabzayı (iki elle tutarken ön tutamağı da) kavrayan eller çizilir. Reload sırasında sol el TACZ animasyonuyla şarjörü taşır.
- **Kol omuza bağlı:** Başkalarının gözünde VR oyuncusunun kolu omuzdan uzanır, yumruğu kabzayı tutar (dirsekli ve dirseksiz Vivecraft modellerinde). Silah havada süzülmez, kol da silahı yutmaz. Bu görüntü izleyenin bilgisayarında çizildiği için arkadaşlarında da aynı sürüm olmalı.
- **Diğer oyuncular:** VR oyuncularının silahı elinde 3D, nişan aldığı yöne dönük görünür. Ateş edince ağız alevi ve sürgü animasyonu, reload'da TACZ reload animasyonu oynar; elle şarjör değiştirirken şarjörün çıktığını ve elindeki yeni şarjörü görürler.
- **Vuruş hissi:** Mermin bir şeye isabet edince kolun titrer ve vuruş sesi gelir; kafaya isabette daha güçlü, öldürünce uzun bir titreşim. Kapatmak için `hitFeedback = false`.
- **VR konforu:** TACZ'ın kamerayı döndüren geri tepmesi ve yürürken silahı sallaması kapalı; silah yukarı teper ve titreşim verir. HUD nişangahı gizli, silahla savurunca blok kırma kapalı.

## Hangi parça hangi silaha uyar

VR'da elinde bir aksesuar (dürbün, susturucu, şarjör, lazer…) tutarken elinin üstünde küçük bir yazı çıkar:
- **✔ Elindeki silaha uyar** ya da **✘ Elindeki silaha uymaz**
- **Uyar:** envanterindeki uyan silahlar, hotbar numarasıyla (çantadakiler "çanta" diye)
- Hiçbirine uymuyorsa: **Envanterindeki hiçbir silaha uymaz**

Kapatmak için `attachmentHints = false`.

## Çift tabanca

İki eline de bir TACZ silahı al (sağ elde bir silah olmalı). Sol eldeki silah sol kontrolcüde durur ve **sol tetikle** ateşlenir. Tabancalar her çekişte bir, otomatik silahlar basılı tuttukça ateş eder. Sol silahın kendi şarjörü var, **A**'ya basınca ikisi birden dolar (sol olan ~1,5 sn'de). Sol elinde silah varken sol tetik ışınlanma yapmaz. Diğer oyuncular sol silahını, sesini ve alevini görür. Kapatmak için sunucu ayarında `dualWield = false`.

## TaCZ VR eşyaları

Hepsi yaratıcı envanterde kendi **TaCZ VR** sekmesinde (VR gözlüklü logo), hepsinin tarifi var. Gözlük dışında hepsi elde, yerde ve atılınca **gerçek 3D model** (pimi, kolu, sapı, anteni olan); envanterde küçük resim olarak görünür. Atılan bombalar havada takla atar, yere düşünce yan yatar.

- **El bombası** (tarif: üstte demir parçacığı, ortada demir-barut-demir, altta demir → 2 bomba): **A**'ya basınca pim çekilir ve fitil yanar (4 sn). Kolunu savurup **A**'yı bırak: bomba elinin gittiği yöne, savurma hızınla uçar, sekerek düşer. Çok beklersen elinde patlar. VR dışında baktığın yöne atılır. Blok kırmaz (`grenade breaksBlocks` ile açılabilir).
- **Flaş bombası** (barut yerine parlak taş tozu): Aynı şekilde atılır, 2,5 saniyede patlar. Gören oyuncuların ekranı bembeyaz olur, zil sesi gelir, yavaşça açılır. Ne kadar yakınsan ve ne kadar dik bakıyorsan o kadar kötü; arkanı dönersen az, duvar arkasındaysan neredeyse hiç etkilemez. VR'da iki gözün de beyazlar. Yakındaki yaratıklar 4 saniye sersemler, seni göremez. Gece görüş gözlüğü takılıyken daha da kötü.
- **Sis bombası** (barut yerine beyaz yün): 2 saniyede açılır, 20 saniye boyunca büyük bir duman bulutu çıkarır. Yaratıklar dumanın içini ve arkasını göremez, iskeletler duman arkasına ok atamaz.
- **Savaş bıçağı** (demir + çubuk, çapraz): Hızlı vurur. VR'da bıçağı ileri **saplamak** yeter; yavaş itmek saplama sayılmaz. Arkadan saplarsan **2,5 kat hasar**.
- **Sağlık iğnesi** (cam şişe + parlayan karpuz + demir parçacığı → 2): 4 kalp iyileştirir, biraz can yenileme verir. VR'da iğneyi **diğer kolunun ön koluna** ya da bir arkadaşına batır. VR dışında basılı tutup kendine, arkadaşına sağ tıklayıp ona.
- **Gece görüş gözlüğü** (demir-kızıltaş-demir, altında iki yeşil cam): Kask yerine takılır. Açıkken karanlıkta görürsün, görüntü yeşildir. Aç/kapa: **B** tuşu (Vivecraft ayarlarında kontrolcüye atanabilir) ya da VR'da **elini gözlüğe götürüp grip**.
- **Kanca** (üç demir, tuzak kancası, kayış): **A** / sağ tık ile elinin gösterdiği yere fırlatılır (VR dışında baktığın yere), 200 bloğa kadar gider, tıkladığın an takılır. Bir bloğa takılınca seni oraya çeker, orada asılı kalırsın, düşme hasarı birikmez. Tekrar **A** ile bırakırsın. Elinden bırakırsan ip kopar.
- **Telsiz** (üstte paratoner, ortada bakır-kızıltaş-bakır, altta bakır-demir-bakır): Elindeyken **kullan tuşunu basılı tut**: telsiz ağzına kalkar, konuştuğun her şeyi envanterinde telsiz olan herkes duyar, ne kadar uzakta olursa olsun. Ses telsiz gibi cızırtılı gelir, basınca ve bırakınca "klik" sesi olur, ekranda kaç kişinin duyduğu yazar. VR'da telsizi **ağzına götürmen** yeterli. Takım maçında sadece takım arkadaşların duyar. 6 bloktan yakındakiler seni zaten normal sesle duyar, telsizden ikinci kez gelmez. Telsiz sesinin seviyesini Simple Voice Chat ayarlarında "Telsiz" kaydırıcısından ayarlayabilirsin.

**Telsiz için Simple Voice Chat gerekir** (`voicechat-forge-1.20.1-2.6.17.jar`, bu klasörde). Herkes kurmalı. İlk açılışta **V**'ye basıp Simple Voice Chat'in kurulumunu bitirin, yoksa ses kapalı sayılır. SVC olmadan mod normal çalışır, sadece telsiz konuşmaz.

## Kalkan, lazer
- **Kalkan:** Sol elindeki normal kalkanı merminin önüne tuttuğun an kurşunu durdurur, "kullanmana" gerek yok. İndirirsen mermi geçer.
- **Görünen lazer:** Silahında lazer takılıysa kırmızı ışını ve noktası herkese (sana da) görünür.

## Oyun menüsü (op / dünyayı açan)

ESC menüsünün sol üstünde **⚔ Oyun** butonu çıkar (op'lara ve dünyayı açan kişiye). Her oyunda 3 saniyelik geri sayım var (bu sürede kimse hasar almaz) ve canı 0'a inen **ölmez, eşyası gitmez**. Oyundan çıkan izleyiciye geçer, oyun bitince herkes eski oyun moduna döner ve iyileşir. **Oyunu durdur** ile istediğin an bitirebilirsin.

- **İlk düşen kaybeder:** Herkes savaşır, biri düşünce oyun biter, kazanan ekranda yazar.
- **Son kalan kazanır:** Düşen oyundan çıkar, en son ayakta kalan kazanır.
- **Takım maçı:** Oyuncular rastgele kırmızı ve mavi takıma ayrılır (isimler renkli). Takım arkadaşını vuramazsın. Düşen oyundan çıkar, bir takımda kimse kalmayınca diğeri kazanır. En az 2 oyuncu lazım.
- **3 can hakkı:** Herkesin 3 canı var. Düşünce bir can gider, hemen iyileşirsin ve 3 saniye hasar almazsın. Canı biten çıkar, son canı kalan kazanır.
- **Zombi dalgaları:** Birlikte zombilere karşı. Her dalgada zombi sayısı artar (4, 6, 8…), 4. dalgadan sonra bazıları kılıçlı. Son 2 zombi parlar. Oyuncular birbirine (ve kendi bombasıyla kendine) hasar veremez. Zorluk Barışçıl olmamalı.
  - **Yere düşme:** Canı biten ölmez, yere düşer ve sürünür. Yerdeyken hasar almaz, zombiler onu görmez, parlar. 30 saniye içinde bir arkadaşı yanında **3 saniye çömelirse** (VR'da elini ona değdirirse) ya da **sağlık iğnesini batırırsa** kalkar. Kalkamazsa oyundan çıkar ve izler. Herkes yere düşerse oyun biter. Dalga bitince yerdekiler kendiliğinden kalkar.
  - **Puan ve dükkan:** Zombi başına 25, boss 300, arkadaşını kaldırmak 50, her dalga sonu herkese dalga × 50 puan. Puanlar sağdaki tabloda. Dalga arasında (15 sn) dükkan kendiliğinden açılır (sonra **J** ile), puanla silah, mermi, bomba, iğne, bıçak alırsın. Herkes **Hazırım** derse sıradaki dalga hemen gelir. **Puanla aldığın her şey oyun bitince geri alınır**; satın aldığın silaha kendi dürbününü taktıysan dürbün sana geri verilir.
  - **Boss:** Her 5 dalgada bir netherite kasklı, kılıçlı, çok canlı bir **Dev Zombi** gelir, üstte can barı çıkar.

## Solak oyuncular

Minecraft'ta **Ana El: Sol** (Main Hand: Left) seçen oyuncular için:

- **Düz ekranda:** Birinci şahısta silah ekranın soluna aynalanır, nişangah ortada kalır.
- **Başkalarının gözünde:** TACZ solak oyuncunun silahını hiç çizmiyordu (eli boş görünüyordu). Artık silah sol elinde görünür. Bu görüntü izleyenin bilgisayarında çizilir, o yüzden herkeste aynı sürüm olmalı.
- **VR'da:** Vivecraft'ta solak modu açıksa kabzayı sol kol, ön tutamağı sağ kol tutar.

Kapatmak için `leftHandedGuns = false`.

## Atmosfer

- **Yankı:** Mağarada ve kapalı yerde silah sesi yankılanır, açıkta kuru çıkar (`gunEcho`).
- **Mermi vızıltısı:** Başkasının mermisi kafanın yakınından geçince vızıldar.
- **Ağız alevinin ışığı:** Karanlıkta ateş edince etraf bir anlığına aydınlanır (`muzzleLight`).
- **Çak bir beşlik:** VR'da elini hızla arkadaşının eline vur: şaplak sesi herkese duyulur, kıvılcım çıkar, ikinizin de kolu titrer (arkadaşın VR'sız olsa da olur) (`highFive`).

## Özel menü (izinli oyuncular)

Dünyanın/sunucunun `config/taczvr-common.toml` dosyasında `[assist] players` listesindeki oyuncular için ESC menüsünün sol üstünde bir buton çıkar:

- **Aim assist (kilitlenme):** Silahı kabaca yönelttiğin taraftaki (75° içinde) görünen **en yakın oyuncuya** kilitlenir; oyuncu yoksa silahın baktığı yere yakın yaratığa. Saçma olmaz, mermi uçarken hedefi takip eder, kaçan hedefi de vurur.
- **Sınırsız mermi (reload yok):** Silah hep dolu kalır.
- **Hedefleri parlat:** 96 blok içindeki canlılar duvar arkasından da parlar.

Listede olmayanlar butonu görmez. Başkasının sunucusunda o sunucunun listesi geçerlidir.

## LesRaisins Tactical Equipments ile birlikte

LesRaisins Tactical Equipments (TACZ eklentisi) yüklüyse, onun bıçakları, sopası, bombaları, sağlık malzemeleri ve kalkanı VR'da elinde **3D** görünür. Kendi modu bunları birinci şahısta sadece kendi el animasyonuyla çiziyor, VR'da o çalışmadığı için eşyalar görünmüyordu. Bu mod onları kontrolcünün yerinde, üçüncü şahısta elde durdukları gibi çizer. LesRaisins'in kendisi değiştirilmez ve bu modun içinde yoktur; ayrı olarak kurulur. Düz ekranda her şey LesRaisins'in kendi çizimidir.

## Kurulum

`mods` klasörüne şunlar:

1. `taczvr-1.20.1-1.4.2.jar` (bu mod; eski sürümü sil, iki sürüm aynı anda durmasın)
2. TACZ `tacz-1.20.1-1.1.8-hotfix` (veya daha yeni 1.1.8+)
3. **Vivecraft 1.20.1 Forge** — `vivecraft-1.20.1-1.3.15-forge.jar`
4. **Simple Voice Chat** — `voicechat-forge-1.20.1-2.6.17.jar` (telsiz için; olmasa da mod çalışır)

Minecraft **1.19.2** için ayrı jar var: `taczvr-1.19.2-<sürüm>.jar` (TACZ 1.1.4-hotfix ve Vivecraft 1.19.2 ile), aynı GitHub sürümlerinde.

**Güncelleme:** Oyun açılınca mod GitHub'da (https://github.com/merthileyapiyor-lab/TaCZ-VR-releases) yeni sürüm var mı diye bakar. Varsa ana menüde **Update TaCZ VR** ekranı çıkar: **Later** ile geçersin, **Quit Game** yeni sürümü indirip mods klasörüne kurar, eskisini siler ve oyunu kapatır. Oyunu tekrar açınca yeni sürümle oynarsın. Kapatmak için `updateCheck = false`.

Çok oyunculu sunucuda mod **sunucuya da** kurulmalı. Mermi yönü, şarjör, aksesuar ve silah verme sunucuda işleniyor. Sunucu ve tüm oyuncular **aynı sürümü** kullanmalı. Tek oyunculuda ekstra bir şey gerekmez.

Diğer oyuncular seni silahla 3D görmek için Vivecraft kullanmalı. Mermi her durumda namludan çıkar, sunucuda Vivecraft olmasa bile.

## Gun pack'ler

TACZ gun pack'leri ayarsız çalışır: mod silah listesi tutmaz, her silahın modelindeki kemikleri (kabza, namlu ucu, nişangah, şarjör) TACZ'ın kendi birinci şahsı gibi okur. VR'da Cold War Guns, Classics Reborn, World War 2 ve Fallout fix ile denendi (218 silah): hepsi elde doğru durur, namludan nişan alır ve namlu ucundan ateş eder; ikisi hariç hepsinde şarjör elle de değişir (o ikisi **A** ile reload olur).

Bir paketin silahı elinde fazla büyük ya da küçükse `config/taczvr-client.toml` içindeki `scaleOverrides`'a kendi boyunu ekle, örneğin `"ww2:kar98k=1.2"`.

World War 2 paketinin zip'inde (1.0.4) bozuk isimli üç dosya var, TACZ açamıyor; zip'i `tacz` klasörüne çıkarınca yüklenir.

## Hizalama ayarı (oyun içinde)

| Komut | Ne yapar |
|---|---|
| `/taczvr` | Mevcut değerleri gösterir |
| `/taczvr scale 0.3` | Silah boyutu (0.3 ≈ 80 cm AK) |
| `/taczvr grip 0 -0.01 0.03` | Kabzanın kontrolcüye göre yeri, metre (X sağ, Y yukarı, Z geri) |
| `/taczvr pitch 10` | Namluyu yukarı/aşağı eğer, derece |
| `/taczvr aimline` | Merminin gideceği yolu kırmızı çizgiyle gösterir |
| `/taczvr reset` | Varsayılanlara döner |

Tüm ayarlar `config/taczvr-client.toml` dosyasında (oyun açıkken düzenlenebilir). Her özellik oradan ayrı ayrı kapatılabilir.

## Bilinen sınırlar

- Oculus/Iris shader'larıyla elde silah ve dürbün görüntüsü sorunlu olabilir.
- Silah verme için elin arkadaşının eline yakınken tetik de "ver" olur (ateş etmez).
- Diğer oyuncular VR oyuncusunun şarjör değiştirmesini ve animasyonlarını görmez (silah onlara hep duruş pozunda görünür).
