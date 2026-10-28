# Kết quả kiểm tra

## Docker Compose ngày 28/10/2026

- Full Maven verify chạy lại hoàn tất 03:07:07 UTC+7: **230 tests, 0 failures/errors/skipped**. Log local `target/compose-maven-verify.log`.
- Build image Linux từ source, không lấy JAR hoặc thư viện cài trên host. Project kiểm thử riêng, PostgreSQL 17.11 và Mailpit; không kết nối container `utex-bookstore-postgres-17`.
- Volume trống tự tạo schema, seed và điều chỉnh ngày demo. App chỉ chạy sau khi dấu khởi tạo hoàn tất được ghi; PostgreSQL không publish cổng ra host.
- `scripts/check-compose.py` đạt trên PostgreSQL: catalog, đăng ký/OTP qua SMTP Mailpit, kích hoạt, JWT, quyền admin/shop, hồ sơ/địa chỉ, version kho cũ, checkout COD 80.000 + 20.000 − 10.000 = 90.000, gửi lặp, giao hàng, yêu cầu trả và ghi nhận hoàn tiền giả có xác nhận.
- Sau `down` rồi `up --wait`, số user/đơn/bản sách và hash khóa JWT/OTP giữ nguyên; đăng nhập lại đọc đúng hồ sơ, đơn hoàn tất và yêu cầu REFUNDED. Không seed lại.
- Dựng lại từ thư mục chỉ có file trong Git cộng duy nhất `config/team.properties`, không có target/JAR/config máy cũ; lệnh Compose một bước và toàn bộ acceptance script tiếp tục đạt trên volume mới thứ hai.
- Quét image config và 18 blob/layer (giải nén gzip khi cần), không thấy giá trị bí mật lấy từ cấu hình local. `config/team.properties` được Git ignore, build context chỉ cho phép pom, source main và entrypoint.
- Không upload Cloudinary, gọi payOS hay gửi email Internet trong kiểm thử Compose. Mailpit là hộp thư thử; trạng thái hoàn tiền là dữ liệu giả. Đã tạo mới/seed database trong volume Compose riêng, **không migrate database cũ**. Kiểm tra trên Docker Desktop Linux containers x86_64; chưa chạy trực tiếp trên máy ARM/macOS.

Cập nhật: 28/10/2026. Full `verify` đạt **230 test**, không failure/error/skipped, hoàn tất 02:39:35 UTC+7. Log local `target/api-final-verified.log`.

## API và phân công ngày 28/10/2026

Bổ sung 10 test HTTP trong ExpandedApiWebTest: public catalog, Bearer/CSRF separation, JSON sai, địa chỉ khác chủ, kho khác shop/version cũ, admin/khóa tài khoản thu hồi JWT, checkout voucher/phí/gửi lặp, trả hàng và xác nhận hoàn tiền ngoài, quyền shop, review và upload, duyệt shop/CRUD catalog. Toàn bộ payment/JWT/WebSocket và test cũ cũng đạt. Đóng gói JAR thành công. Không chạy migration hoặc thanh toán thật. Xem [nghiệm thu](FINAL_ACCEPTANCE.md) và [API](API_REFERENCE.md).

Các nhật ký mốc cũ xem lịch sử Git. Lượt cập nhật tên, rubric và dọn tài liệu không đổi source ứng dụng; không chạy lại test ứng dụng chỉ vì sửa tài liệu.
