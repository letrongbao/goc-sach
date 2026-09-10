# Kết quả kiểm tra

Cập nhật: 10/09/2026.

## Kết quả hiện tại

- `mvnw.cmd -B -ntp verify`: **127/127 test đạt**, 0 lỗi, 0 bỏ qua; đóng gói `target/goc-sach.jar` thành công.
- Đã sửa lỗi dọn thư mục khóa JWT tạm trên Windows; full build không còn đỏ do `AccessDeniedException`.
- Đã kiểm tra preview thật Chrome ở desktop và mobile 390 px: trang chủ, đăng nhập, chi tiết sách, thêm giỏ, giỏ hàng và checkout; không có lỗi console.
- Giỏ hàng hiển thị tên sách/cửa hàng, tổng tiền, trạng thái hết bán và thao tác bỏ. Checkout giữ cùng idempotency key khi tải lại form.
- Checkout khóa các bản sách theo ID tăng dần, tách đơn theo shop và chỉ trả phóng bản có reservation còn hoạt động khi hủy.
- Xóa địa chỉ mặc định sẽ chọn địa chỉ còn lại làm mặc định. Xem chi tiết khi đăng nhập sẽ cập nhật danh sách đã xem.
- API đọc review công khai; API tạo review vẫn yêu cầu JWT. CSP ảnh chỉ cho phép nội bộ, data preview và Cloudinary HTTPS.
- JPA tiếp tục dùng `hibernate.hbm2ddl.auto=validate`; không có SQL hoặc migration nào được chạy trong lượt kiểm tra.

## Phần Chưa Nghiệm Thu Thật

- PostgreSQL `goc_sach`: chưa xác nhận schema, transaction cạnh tranh, rollback và dữ liệu sau restart trong lượt này.
- Gmail/Cloudinary: test adapter và cấu hình không thay cho việc gửi OTP/upload-xóa asset thật bằng ứng dụng.
- Các phần cuối kỳ còn thiếu: trang shop công khai, bộ lọc/top 20 đầy đủ, vận hành đơn Seller/Manager, carrier, voucher, VNPAY, trả/hoàn tiền, media review và thông báo WebSocket.
- Một số controller cũ và `App`/`Store` service locator vẫn cần tiếp tục tách theo module; đây là nợ kiến trúc, không phải lỗi build hiện tại.

## Cách Kiểm Tra Lại

1. Chạy `mvnw.cmd -B -ntp verify`.
2. Chạy `scripts/preview.ps1` để xem UI không cần database.
3. Sau khi chủ dự án tự chuẩn bị database/schema, chạy JAR với `config/local.properties` và nghiệm thu PostgreSQL, Gmail, Cloudinary bằng dữ liệu kiểm thử.

Chi tiết phạm vi còn lại nằm trong [MILESTONES.md](MILESTONES.md) và [ROADMAP.md](ROADMAP.md).
