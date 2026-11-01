"""Danh sách domain 18+ chọn tay, dùng chung cho sinkhole Windows và bộ lọc Android.

Danh sách này KHÔNG đầy đủ. Nó bắt những nền tảng lớn mà một người chủ động tìm sẽ gõ tay,
phần còn lại trông cậy vào bộ lọc gia đình của upstream (ADULT_UPSTREAMS trong net.py).
Chỉ ghi domain gốc, không ghi www, không ghi đường dẫn: subdomain đã được phủ theo cơ chế
kế thừa của domain_matches.

android/app/src/main/res/raw/adult_domains.txt là bản sinh từ file này. Sửa xong thì chạy
`python tools/sync_adult_list.py`; tests/test_adult_list.py giữ cho hai bên không lệch nhau.
"""

from __future__ import annotations

ADULT_DOMAINS: tuple[str, ...] = (
    # Video khiêu dâm
    "pornhub.com",
    "xvideos.com",
    "xvideos.es",
    "xnxx.com",
    "xhamster.com",
    "xhamster2.com",
    "xhamster3.com",
    "redtube.com",
    "youporn.com",
    "tube8.com",
    "spankbang.com",
    "spankwire.com",
    "beeg.com",
    "eporner.com",
    "mofosex.com",
    "motherless.com",
    "hclips.com",
    "tnaflix.com",
    "keezmovies.com",
    "sunporno.com",
    "vporn.com",
    "upornia.com",
    "youjizz.com",
    "gotporn.com",
    "thumbzilla.com",
    "efukt.com",
    "hellporno.com",
    "porn300.com",
    "fapping.club",
    "yespornplease.com",
    "iloveporn.xxx",
    "xmovie.com",
    "xmovie2.tv",
    "porn.com",
    "sex.com",
    "xxx.com",
    "nuvid.com",
    "xbunker.com",
    "pornerbros.com",
    "fux.com",
    # Studio và mạng lưới nội dung trả phí
    "brazzers.com",
    "bangbros.com",
    "naughtyamerica.com",
    "fakehub.com",
    "tushy.com",
    "blacked.com",
    "deeper.com",
    "vixen.com",
    "girlsway.com",
    "darkx.com",
    "penthouse.com",
    "playboy.com",
    "welovetantras.com",
    "wrtheflies.com",
    # Nền tảng sáng tạo và ảnh đặt riêng
    "onlyfans.com",
    "fansly.com",
    "justfor.fans",
    # Cam trực tuyến
    "chaturbate.com",
    "bongacams.com",
    "stripchat.com",
    "myfreecams.com",
    "livejasmin.com",
    "camsoda.com",
    "cam4.com",
    "jerkmate.com",
    "flirt4free.com",
    "bongaurls.com",
    "cambb.xxx",
    # Cộng đồng và hẹn hò người trưởng thành
    "adultfriendfinder.com",
    "fetlife.com",
    "seeking.com",
    "ashleymadison.com",
    # Truyện, ảnh và hoạt hình người lớn
    "literotica.com",
    "rule34.xxx",
    "e621.net",
    "sankakucomplex.com",
    "gelbooru.com",
    "booru.org",
    "nhentai.net",
    "hanime.tv",
    "javhd.com",
)
