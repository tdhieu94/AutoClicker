# Tự động chạm trên iPhone/iPad (không cần cài app)

## Vì sao không có app AutoClick cho iOS?

Apple không cho ứng dụng bên thứ ba chạm vào ứng dụng khác. Trên iOS không có API nào giống
Accessibility Service của Android, nên mọi app "auto clicker" trên App Store chỉ tự chạm được
**bên trong chính nó**. Cách duy nhất ngoài jailbreak là dùng tính năng có sẵn của iOS:
**Điều khiển công tắc (Switch Control)** với **Cử chỉ tùy chỉnh** và **Công thức (Recipes)**.

## Bước 1: Ghi lại chuỗi chạm (Cử chỉ đã lưu)

1. Mở **Cài đặt → Trợ năng → Điều khiển công tắc → Cử chỉ đã lưu → Tạo cử chỉ mới**.
2. Chạm vào các vị trí cần auto-click theo đúng thứ tự (có thể chạm cùng một điểm nhiều lần).
   Mỗi cử chỉ ghi được tối đa khoảng 20 giây.
3. Bấm **Lưu**, đặt tên, ví dụ "AutoClick".

> Mẹo: mở app cần auto-click trước, chụp màn hình, rồi xem ảnh đó khi ghi để biết chính xác vị trí cần chạm.

## Bước 2: Tạo Công thức

1. **Cài đặt → Trợ năng → Điều khiển công tắc → Công thức → Tạo công thức mới**.
2. Đặt tên, chọn **Gán công tắc → Toàn màn hình → Cử chỉ tùy chỉnh** (hoặc chọn cử chỉ "AutoClick" đã lưu).
3. Ở mục **Công thức → Khởi chạy công thức**, chọn công thức vừa tạo.

## Bước 3: Thêm nút tắt bật nhanh

**Cài đặt → Trợ năng → Phím tắt trợ năng → chọn "Điều khiển công tắc"**.
Bấm 3 lần nút sườn (hoặc nút Home) để bật/tắt.

## Bước 4: Dùng

1. Mở app cần auto-click.
2. Bấm 3 lần nút sườn để bật Điều khiển công tắc.
3. Chạm vào màn hình một lần: iPhone sẽ phát lại toàn bộ chuỗi chạm đã ghi.
   Chạm lại để phát lại tiếp.
4. Bấm 3 lần nút sườn để tắt.

## Giới hạn so với bản Android

| | Android (app) | iOS (Switch Control) |
|---|---|---|
| Chạy lặp vô hạn | Có | Không, mỗi lần chạm màn hình phát lại một chuỗi ~20 giây |
| Chỉnh khoảng thời gian (ms) | Có | Theo nhịp lúc ghi |
| Nhiều điểm chạm | Có | Có (ghi trong cùng một cử chỉ) |
| Cần cài đặt | Có (APK) | Không |

Nếu cần chạy liên tục trên iOS thì chỉ có jailbreak (vd. tweak như AutoTouch). Việc đó làm mất bảo
hành, giảm bảo mật và nhiều game/app ngân hàng sẽ chặn, nên không khuyến khích.
