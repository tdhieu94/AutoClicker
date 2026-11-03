# AutoClicker — Android + iOS

## Android

App Kotlin gốc, dùng **Accessibility Service** + `dispatchGesture()` để chạm vào **bất kỳ ứng dụng nào**.
Không cần root, không cần quyền "Hiển thị trên ứng dụng khác" (bảng điều khiển dùng cửa sổ trợ năng).

**Tính năng**
- Nhiều điểm chạm, chạm lần lượt theo thứ tự 1 → 2 → 3…; kéo thả để đặt vị trí.
- Chỉnh khoảng cách giữa các lần chạm (tối thiểu 10 ms), thời gian giữ, số vòng lặp (0 = chạy mãi).
- Lệch ngẫu nhiên quanh điểm (px) để chạm tự nhiên hơn.
- Bảng điều khiển nổi: ≡ kéo, ▶/■ chạy/dừng, ＋ thêm điểm, － xoá điểm, ✕ ẩn.
- Android 7.0 (API 24) trở lên.

**Cài đặt trên điện thoại**
1. Cài file `.apk` (cho phép "Cài ứng dụng không rõ nguồn gốc" nếu được hỏi).
   Nếu Google Play Protect báo *"Đã chặn ứng dụng để bảo vệ thiết bị"*: mở CH Play → ảnh đại diện →
   Play Protect → ⚙ → tắt *"Quét ứng dụng bằng Play Protect"*, cài lại APK, rồi bật Play Protect lại.
   (Google chặn mọi app cài ngoài CH Play có xin quyền Trợ năng ở một số nước, trong đó có Việt Nam.)
2. Mở AutoClicker → bấm **"1. Bật dịch vụ Trợ năng"** → tìm AutoClicker → bật.
   Trên Android 13+, nếu nút bật bị mờ: vào *Cài đặt → Ứng dụng → AutoClicker → ⋮ → Cho phép cài đặt bị hạn chế*, rồi bật lại.
3. Quay lại app → **"2. Lưu & hiện bảng điều khiển nổi"**.
4. Mở app cần auto-click, kéo vòng tròn số tới chỗ cần chạm, bấm ▶.

**Build APK**
- Android Studio: mở thư mục `android/`, bấm *Run* hoặc *Build → Build APK(s)*.
- Dòng lệnh: `cd android && ./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
- GitHub: đẩy thư mục này lên một repo, workflow `.github/workflows/android.yml` sẽ tự build và đính kèm APK
  trong tab *Actions*.

> Bản release đang ký bằng debug key để cài thử. Muốn đưa lên Google Play cần keystore riêng, và Google
> Play kiểm duyệt rất chặt các app dùng Accessibility API.

## iOS

Apple không cho app bên thứ ba chạm vào app khác, nên không thể làm app AutoClick cho iOS như Android.
Cách khả thi là dùng tính năng có sẵn **Điều khiển công tắc (Switch Control)**. Xem
[ios/HUONG_DAN_IOS.md](ios/HUONG_DAN_IOS.md).

## Lưu ý

Nhiều game và app cấm auto-click trong điều khoản sử dụng và có thể khoá tài khoản. Hãy tự cân nhắc khi dùng.
