# Đưa AutoClicker lên CH Play (có quảng cáo AdMob)

Mã nguồn đã sẵn sàng. Mỗi lần đẩy mã lên `main`, GitHub Actions build:

| File | Dùng cho |
|---|---|
| `AutoClicker.apk` | Bản GitHub: tự cập nhật, không quảng cáo, cài tay. |
| `AutoClicker-play.aab` | Bản CH Play: có quảng cáo, không tự cập nhật. Tải lên Play Console. |

Cả hai nằm ở [Releases → latest](https://github.com/tdhieu94/AutoClicker/releases/tag/latest).

Bản CH Play có tên gói `com.hieu.autoclicker.play` (khai báo trong `android/app/build.gradle.kts`).
**Tên gói không đổi được sau lần tải lên đầu tiên**, nên nếu muốn tên khác thì báo đổi trước.

---

## 1. Tạo tài khoản

1. **Google Play Console**: <https://play.google.com/console/signup>, phí 25 USD một lần.
   - Chọn tài khoản **Cá nhân** (đơn giản nhất) hoặc **Tổ chức** (cần mã D-U-N-S).
   - Xác minh danh tính bằng CCCD và xác minh số điện thoại.
   - Tài khoản cá nhân mới phải **thử nghiệm kín với ít nhất 12 người trong 14 ngày liên tục**
     trước khi được xin phát hành công khai (mục 7).
2. **Google AdMob**: <https://admob.google.com>, đăng nhập cùng tài khoản Google.
   - Điền thông tin thanh toán: tên, địa chỉ, tài khoản ngân hàng Việt Nam (nhận USD, SWIFT code).
   - AdMob chuyển tiền khi số dư đạt 100 USD.

## 2. Tạo đơn vị quảng cáo và thay mã thật

Hiện app đang dùng **mã thử nghiệm** của Google: quảng cáo hiện chữ "Test Ad" và **không có doanh thu**.

1. AdMob → **Ứng dụng → Thêm ứng dụng** → Android → "Ứng dụng chưa có trên cửa hàng" (sau khi lên CH Play thì liên kết lại).
2. Tạo 2 đơn vị quảng cáo:
   - **Biểu ngữ (Banner)**, đặt tên "Banner màn hình chính".
   - **Quảng cáo xen kẽ (Interstitial)**, đặt tên "Mở bảng điều khiển".
3. Mở file [`android/gradle.properties`](../android/gradle.properties) trên GitHub (biểu tượng bút chì để sửa) và thay 3 dòng:
   ```
   admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY      # Mã ứng dụng (có dấu ~)
   admobBannerId=ca-app-pub-XXXXXXXXXXXXXXXX/1111111111   # Mã đơn vị banner
   admobInterstitialId=ca-app-pub-XXXXXXXXXXXXXXXX/2222222222
   ```
   Các mã này không phải bí mật, để công khai trong repo được. Bấm **Commit**, GitHub sẽ tự build lại file AAB.
4. **Đừng tự bấm vào quảng cáo của mình**, và đừng dùng AutoClicker để bấm quảng cáo. AdMob phát hiện là khoá tài khoản vĩnh viễn.
   Khi tự thử app, thêm điện thoại của bạn làm **thiết bị thử nghiệm** trong AdMob → Cài đặt → Thiết bị thử nghiệm.

## 3. Bật trang chính sách quyền riêng tư (GitHub Pages)

1. Mở <https://github.com/tdhieu94/AutoClicker/settings/pages>.
2. **Source**: *Deploy from a branch* → Branch **main**, thư mục **/docs** → **Save**.
3. Sau 1–2 phút, các trang sau sẽ hoạt động:
   - Trang giới thiệu: <https://tdhieu94.github.io/AutoClicker/>
   - **Chính sách quyền riêng tư** (dán vào Play Console): <https://tdhieu94.github.io/AutoClicker/privacy-policy.html>

## 4. app-ads.txt (để AdMob trả đủ tiền)

AdMob yêu cầu file `app-ads.txt` nằm ở **gốc** tên miền "Trang web" khai báo trong CH Play.
Trang GitHub Pages của repo nằm ở thư mục con, nên cần thêm một repo riêng:

1. Tạo repo công khai tên đúng là **`tdhieu94.github.io`**.
2. Thêm file `app-ads.txt` (mẫu ở [`docs/app-ads.txt`](app-ads.txt)), thay `pub-XXXXXXXXXXXXXXXX` bằng mã nhà xuất bản
   trong AdMob → Ứng dụng → app-ads.txt.
3. Bật Pages cho repo đó (Branch main, thư mục / root).
4. Trong Play Console → Thông tin cửa hàng → **Trang web**: `https://tdhieu94.github.io`.

## 5. Tạo ứng dụng trong Play Console

**Tạo ứng dụng**: Tên "AutoClicker – Tự động chạm" (tối đa 30 ký tự), ngôn ngữ mặc định Tiếng Việt, *Ứng dụng*, *Miễn phí*.

### Nội dung ứng dụng (App content)

| Mục | Trả lời |
|---|---|
| Chính sách quyền riêng tư | `https://tdhieu94.github.io/AutoClicker/privacy-policy.html` |
| Quyền truy cập ứng dụng | Tất cả chức năng dùng được, không cần đăng nhập |
| Quảng cáo | **Có**, ứng dụng chứa quảng cáo |
| Xếp hạng nội dung | Danh mục *Tiện ích/Năng suất*, trả lời "Không" cho bạo lực, nội dung người lớn… |
| Đối tượng mục tiêu | **18 tuổi trở lên** (tránh các quy định dành cho trẻ em) |
| Ứng dụng tin tức / chính phủ / tài chính / sức khoẻ | Không |
| Mã nhận dạng quảng cáo | **Có**, dùng cho *Quảng cáo hoặc tiếp thị* |

### An toàn dữ liệu (Data safety)

Do AdMob thu thập (theo [hướng dẫn của Google](https://developers.google.com/admob/android/privacy/play-data-disclosure)):

- Ứng dụng **có** thu thập hoặc chia sẻ dữ liệu: **Có**.
- Dữ liệu được mã hoá khi truyền: **Có**.
- Cách yêu cầu xoá dữ liệu: không có tài khoản; dữ liệu trong app bị xoá khi gỡ app.
- Các loại dữ liệu (đánh dấu **Thu thập** và **Chia sẻ**, mục đích *Quảng cáo hoặc tiếp thị*, *Phân tích*, *Chống gian lận, bảo mật*):
  - **Vị trí → Vị trí gần đúng** (suy ra từ IP)
  - **Hoạt động trong ứng dụng → Tương tác với ứng dụng**
  - **Thông tin và hiệu suất ứng dụng → Nhật ký sự cố, Chẩn đoán**
  - **Thiết bị hoặc mã nhận dạng khác → Mã nhận dạng thiết bị** (Advertising ID)

### Khai báo quyền Trợ năng (quan trọng nhất)

Play Console → Nội dung ứng dụng → **Khai báo quyền → Accessibility API**. Mẫu câu trả lời (tiếng Anh vì người duyệt đọc tiếng Anh):

> AutoClicker is an automation tool that performs taps and swipes at screen positions that the user places
> manually with on-screen markers. The AccessibilityService is used only to (1) dispatch the user-configured
> gestures via `dispatchGesture()` when the user presses Start, and (2) show the floating control panel
> (`TYPE_ACCESSIBILITY_OVERLAY`). The service does not read window content (`canRetrieveWindowContent=false`),
> does not log input, and does not collect or transmit any data. The app does not plan or decide actions on
> its own: every action is explicitly defined and started by the user. Before the user is sent to the
> Accessibility settings, the app shows a prominent disclosure explaining this use and asks for consent.
> The service is declared with `isAccessibilityTool="false"`.

Kèm **video** (đăng YouTube chế độ *Không công khai*, dài 30–60 giây) quay màn hình:
1. Mở app → bấm "1. Bật dịch vụ Trợ năng" → hộp giải thích hiện ra → bấm **Đồng ý** → bật AutoClicker trong Cài đặt.
2. Quay lại app → "2. Lưu & hiện bảng điều khiển nổi" → đặt 2–3 điểm → ▶ → app tự chạm → ■ dừng.

## 6. Thông tin cửa hàng

- **Mô tả ngắn** (≤ 80 ký tự):
  `Tự động chạm và vuốt nhiều điểm, lưu kịch bản, hẹn giờ. Không cần root.`
- **Mô tả đầy đủ** (gợi ý):
  ```
  AutoClicker giúp bạn tự động chạm và vuốt tại các vị trí bạn chọn trên màn hình, không cần root.

  • Nhiều điểm chạm, chạy lần lượt theo thứ tự
  • Thao tác vuốt với điểm đầu và điểm cuối
  • Chỉnh khoảng cách giữa các lần chạm từ 10 ms, số vòng lặp, thời gian giữ
  • Lưu và tải lại kịch bản
  • Hẹn giờ tự dừng, bộ đếm số thao tác
  • Bảng điều khiển nổi gọn nhẹ, chỉnh thông số ngay trên màn hình

  Quyền Trợ năng: AutoClicker dùng API Trợ năng của Android chỉ để thực hiện thao tác chạm/vuốt
  tại các điểm bạn tự đặt và hiển thị bảng điều khiển nổi. Ứng dụng không đọc nội dung màn hình
  và không thu thập dữ liệu cá nhân qua quyền này.
  ```
  **Không** viết những câu như "hack game", "treo máy cày game", "bỏ qua chống bot" hay nhắc tên game/app cụ thể:
  dễ bị từ chối vì vi phạm chính sách hoặc thương hiệu.
- **Biểu tượng** 512×512 PNG, **ảnh nổi bật** 1024×500, **ít nhất 2 ảnh chụp màn hình** điện thoại.

## 7. Thử nghiệm và phát hành

1. **Thử nghiệm nội bộ**: tải `AutoClicker-play.aab` lên, thêm email của bạn, cài thử từ link Play.
   - Lần tải lên đầu, Play Console hỏi về **Ký ứng dụng bởi Google Play**: chọn đồng ý. Khoá trong GitHub Secrets
     trở thành *khoá tải lên* (upload key). Giữ bản sao `autoclicker-release.jks` cẩn thận.
2. **Thử nghiệm kín**: tạo danh sách ít nhất **12 người** (email Google), gửi link tham gia,
   họ phải **giữ app cài liên tục 14 ngày**.
3. Sau 14 ngày: Play Console → **Bảng điều khiển → Xin quyền truy cập phát hành công khai** → trả lời vài câu hỏi
   về đợt thử nghiệm → chờ duyệt (thường vài ngày).
4. Mỗi lần cập nhật: đẩy mã lên GitHub → tải `AutoClicker-play.aab` mới ở Releases → Play Console → tạo bản phát hành mới.
   Số phiên bản tự tăng nên không bị trùng.

## Nếu bị từ chối

Google thường từ chối app dùng Trợ năng vì: thiếu hộp giải thích trước khi bật quyền, video không cho thấy hộp đó,
hoặc mô tả không khớp với cách dùng quyền. Gửi lại email từ chối để sửa theo đúng lý do.
