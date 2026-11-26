# GlobalBrief — Ứng dụng Android đọc báo song ngữ EN–VI tự động hằng ngày

Ứng dụng đọc tin tức trên Android (**Kotlin + Jetpack Compose + Material 3**) tự động thu thập, chọn lọc và tóm tắt song ngữ **Tiếng Anh & Tiếng Việt** hằng ngày cho **4 chuyên đề trọng tâm**:

1. **AI & Tech** (Trí tuệ nhân tạo)
2. **Space Science** (Khoa học vũ trụ)
3. **Climate** (Khí hậu)
4. **Conflicts & War** (Xung đột & Chiến tranh)

## Quy chuẩn nội dung
- **Tối đa 10 tin nổi bật / chuyên đề** (tối đa 40 tin mỗi ngày).
- **Mỗi tin không quá 2 trang điện thoại (không kể hình ảnh)**: gồm tiêu đề EN/VI, tóm tắt 2 đoạn ngắn, tối đa 3 điểm chính và 1 câu tầm ảnh hưởng.
- **Hiển thị song song EN – VI** để tiện đối chiếu và trau dồi tiếng Anh chuyên ngành (hỗ trợ cả dạng xếp dọc từng đoạn và 2 cột song song).
- **Kho lưu trữ tin quan trọng & xuất file về điện thoại**:
  - Lưu từng bài hoặc bấm biểu tượng **Chọn nhiều** để lọc, tích chọn và lưu hàng loạt vào **Kho lưu trữ (`Đã lưu`)**.
  - Đánh dấu **⭐ Quan trọng**, thêm **ghi chú cá nhân**, lọc theo chuyên đề / mức độ quan trọng / từ khóa; dữ liệu được lưu độc lập với bản tin hằng ngày và **chỉ xóa khi bạn bấm Xóa và xác nhận**.
  - Hỗ trợ **Xuất file Markdown (`.md`)** lưu trực tiếp vào bộ nhớ điện thoại (`Downloads` / `Documents`).
- **Tự động cập nhật miễn phí qua GitHub Actions + Gemini API**, app Android tải dữ liệu JSON tĩnh về đọc nhanh và lưu cache offline.
  - Pipeline gộp **1 request / chuyên đề** (= **4 requests/ngày**, khoảng 10K–14K tokens/ngày), kèm khoảng nghỉ giữa các chuyên đề, retry khi gặp 429 và fallback tự động sang `gemini-3.5-flash-lite` / `gemini-2.5-flash` để tránh cạn quota Free Tier.

---

## Cấu trúc dự án

- `pipeline/` — Script Python thu thập RSS uy tín (MIT Tech Review, The Verge AI, Ars Technica, NASA, Space.com, Carbon Brief, The Guardian, BBC, Al Jazeera, Reuters), lọc trùng lặp, xếp hạng và gọi **Gemini API** (`google-genai`) để tạo bản tin song ngữ EN–VI.
- `data/` — Dữ liệu xuất bản hằng ngày (`daily_digest.json`, `manifest.json`, và `archive/YYYY-MM-DD.json`).
- `android/` — Dự án Android Native (Kotlin, AGP 9.0, Compose Material 3, Coil 3, OkHttp, Kotlinx Serialization).
- `.github/workflows/`
  - `daily-news.yml`: Tự động chạy lúc **06:00 sáng giờ Việt Nam (23:00 UTC)** hằng ngày.
  - `build-android.yml`: Tự động chạy unit test và build file `app-debug.apk`.

---

## Hướng dẫn kích hoạt tự động (100% miễn phí)

### Bước 1: Đưa mã nguồn lên GitHub
1. Tạo một repository mới trên GitHub (ví dụ `AI_News`, để chế độ **Public** để app tải `raw.githubusercontent.com` trực tiếp không cần token).
2. Push toàn bộ thư mục dự án lên nhánh `main`.

### Bước 2: Gắn Gemini API Key
1. Lấy API Key miễn phí tại [Google AI Studio](https://aistudio.google.com/).
2. Vào GitHub Repo → **Settings → Secrets and variables → Actions → New repository secret**.
3. Tạo secret tên `GEMINI_API_KEY` và dán API key vào.
4. Vào tab **Actions → Daily Bilingual News Digest → Run workflow** để chạy thử lần đầu.

### Bước 3: Tải file APK cài lên điện thoại Android
1. Vào tab **Actions → Build Android APK → Run workflow** (hoặc chờ build tự động sau khi push).
2. Khi chạy xong, tải artifact **`GlobalBrief-debug-apk`** về giải nén lấy `app-debug.apk` và cài lên điện thoại Android.
3. *(Tùy chọn)* Bạn cũng có thể mở thư mục `android/` bằng **Android Studio** để build hoặc cài trực tiếp qua cáp USB (`adb`).

### Bước 4: Kết nối app với dữ liệu tự động của bạn
1. Mở app **GlobalBrief** trên điện thoại → bấm biểu tượng **Cài đặt (Settings)**.
2. Nhập **Feed Base URL** dạng:
   ```text
   https://raw.githubusercontent.com/<TEN_GITHUB_CUA_BAN>/<TEN_REPO>/main/data/
   ```
3. Bấm **Lưu & Cập nhật**. Từ đó trở đi, mỗi lần mở app hoặc bấm làm mới, app sẽ tải bản tin song ngữ mới nhất của ngày hôm nay (và lưu offline các ngày trước).

*(Mẹo: Bạn cũng có thể điền sẵn URL trên vào hằng số `DEFAULT_FEED_BASE_URL` trong [DigestModels.kt](android/app/src/main/java/com/ainews/globalbrief/data/model/DigestModels.kt) trước khi build APK để không phải nhập tay trên điện thoại).*

---

## Chạy thử & kiểm thử trên máy tính

```powershell
# Chạy unit tests của pipeline
.\.venv\Scripts\python.exe -m pytest pipeline/tests -v

# Chạy tạo bản tin thử nghiệm bằng Gemini API (nếu đã set $env:GEMINI_API_KEY)
.\.venv\Scripts\python.exe pipeline/generate_digest.py --output-dir data --max-per-topic 10 --sync-android-asset android/app/src/main/assets/sample_digest.json

# Hoặc chạy chế độ không cần API key (trích xuất RSS)
.\.venv\Scripts\python.exe pipeline/generate_digest.py --output-dir data --no-llm --sync-android-asset android/app/src/main/assets/sample_digest.json
```
