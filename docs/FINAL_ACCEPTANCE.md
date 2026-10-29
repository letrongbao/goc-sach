# Nghiệm thu đồ án cuối kỳ Góc Sách

Chuẩn bị ngày 26/10/2026; cập nhật API, phân công và hồ sơ ngày 28/10/2026. Chỉ phạm vi nhóm 27, đề tài 14. Các tài liệu kỹ thuật và báo cáo đã được đồng bộ với bản bàn giao.

## API và phân công cập nhật 28/10

- Đã có [Docker Compose một lệnh](DOCKER_QUICKSTART.md) cho nhóm: nhận riêng `config/team.properties`, chạy app/PostgreSQL/Mailpit, tự tạo JWT và seed volume mới. Nghiệm thu bản clone sạch và giữ dữ liệu sau recreate đạt; full verify chạy lại 03:07:07 vẫn 230/230. Xem [kết quả Docker](VERIFICATION.md).
- Lê Trọng Bảo — 22110106: checkout, đơn buyer/seller, payOS/webhook, trả hàng, ghi nhận hoàn tiền, thông báo.
- Nguyễn Kiều Thành — 22110231: tài khoản, hồ sơ/địa chỉ, catalog/tìm kiếm/top20, giỏ, yêu thích/đã xem, review/media.
- Nguyễn An Khang — 22110160: shop/thành viên/kho, catalog admin, voucher/hãng giao/chiết khấu/doanh thu, khóa user và kiểm duyệt.
- [API_REFERENCE.md](API_REFERENCE.md) ghi URL đầy đủ, body, query và quyền; [API_ROUTES.json](API_ROUTES.json) lưu danh mục máy đọc. Xác thực/OTP giữ form session + CSRF; REST riêng dùng Bearer.
- Bổ sung controller gọi service hiện có; checkout JSON nhận carrierId/voucherCode, review nhận JSON/multipart. Hoàn tiền yêu cầu settled=true; xử lý status null trả 400.
- ExpandedApiWebTest: 10 test mới trên HTTP server/RSA tạm, kiểm tra IDOR, shop/admin, version kho, thu hồi token, checkout gửi lặp và hoàn tiền, CRUD và quyền upload. Không có migration hoặc sửa dữ liệu thật trong lượt này.
- [Báo cáo đồ án cuối kỳ](reports/README.md) gồm nội dung hệ thống, phân công, prompt của ba thành viên và phụ lục URL API trong một DOCX. Rubric dùng tab `27` trong bảng online: đề tài 14, ba tên/MSSV, GitHub và điểm tự chấm. Bản XLSX local không dùng để nộp.

## Chức năng bổ sung

- Bốn top 20 mới nhất, bán chạy, yêu thích, đánh giá; mỗi trang 10 đầu sách. Trang chủ có sách bán trên 10 bản.
- Lọc đơn theo trạng thái; chi tiết đơn hiển thị hãng giao, voucher và tiền giảm đã chốt.
- Voucher theo shop, bảng phí giao hàng nội bộ, snapshot chiết khấu và thống kê doanh thu.
- Trả hàng trong 7 ngày cho đơn đã giao và PAID; ghi nhận hoàn tiền bên ngoài có tham chiếu. Sách trả về ngừng bán để kiểm tra.
- Admin khóa user, tăng auth_version, ẩn/hiện review. Quyền kiểm tra ở service.
- Review ảnh/MP4 tối đa 5 MB qua Cloudinary; adapter video có test, chưa nghiệm thu upload thật.
- Native WebSocket `/ws` theo phiên, kiểm tra thay đổi mỗi 5 giây, chỉ phát trạng thái đơn được phép xem; thay cấu hình STOMP chưa dùng trước đây.

## Bằng chứng

`mvnw.cmd -B -ntp verify` hoàn tất 28/10/2026 02:39:35: **230 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Log local `target/api-final-verified.log`; XML `target/surefire-reports/`. Lần sandbox chặn socket không được dùng làm kết luận; lần hoàn chỉnh ngoài sandbox đạt.

`FinalDatabaseAcceptance` trên PostgreSQL demo: validate mapping, top, voucher/phí checkout, trả hàng, fake payOS PAID/callback lặp/hủy. Toàn bộ thao tác nghiệm thu rollback, dữ liệu seed giữ nguyên. Log `target/final-db-acceptance.log`.

Chrome preview đã kiểm tra trang chủ, đăng nhập, top bán chạy, lọc đơn, checkout voucher (315.000 + 40.000 - 10.000 = 345.000 đồng), quản trị và vận hành shop. Ảnh chụp thật nằm trong DOCX. FinalWebTest kiểm tra HTTP, quyền/CSRF và WebSocket cập nhật riêng.

Ngày 27/10: JAR production khởi động với JPA validate trên `goc_sach_final_demo`, port 18082; GET `/rankings/bestselling` trả HTTP 200. Cấu hình demo tắt payOS, SMTP trỏ localhost; Cloudinary dùng cấu hình sẵn có vì JAR bắt buộc bật adapter, không upload trong phép thử. `config/final-demo.properties` bị Git ignore và không nằm trong gói source.

Lỗi PostgreSQL scale BigDecimal 10000/10000.00 gây fingerprint khác nhau đã sửa bằng `stripTrailingZeros().toPlainString()` và thêm test hồi quy. Chi tiết đơn bổ sung breakdown giảm giá/hãng giao để đối chiếu tổng.

JAR mới ngày 28/10 khởi động với JPA validate trên database demo riêng, port18083. Đọc API `/api/categories`, `/api/products`, `/api/products/1`, `/api/rankings/bestselling`, `/api/shops` đều HTTP200; `/api/account/profile` không Bearer trả401. Chỉ kiểm tra đọc, không thay dữ liệu demo; không coi đây là kiểm tra ghi API trên PostgreSQL. Chi tiết local `target/api-jar-http.json`.

## Dữ liệu và migration

- Đã sao lưu `goc_sach` vào `target/backups/goc-sach-before-final-20261026.dump`, không đưa backup vào gói nộp.
- **Đã tạo mới `goc_sach_final_demo` và áp dụng schema/seed tại đây:** 6 tài khoản, 24 đầu sách, 6 danh mục, 3 shop, 84 bản và 18 đơn giả.
- **Chưa migrate `goc_sach` hiện hữu.** Không reset database khác. Đọc, sao lưu rồi áp dụng `sql/operations-manual.sql` có chủ đích nếu nâng cấp dữ liệu cũ.
- Schema chỉ dành cho database trống; demo-final.sql chỉ chấp nhận `goc_sach_final_demo` chưa có users. Không chạy seed lần hai.
- Accounts: admin, reader, buyer, seller, reviewer, manager; mật khẩu demo-password-123. GOCSACH10 giảm 10.000 từ 50.000 tại shop 1, tối đa 30 lượt; có hạn theo seed.

## Thanh toán và giới hạn

Fake gateway kiểm tra thành công, hủy, chữ ký sai, sai tiền, callback lặp/đến muộn, lỗi API; không chuyển tiền thật. Endpoint giả chỉ trong test-source, không đóng vào production JAR. Probe đọc payOS trả HTTP 200/mã 101 cho đơn chưa phát hành; chưa nghiệm thu nhận tiền thật hoặc webhook HTTPS công khai.

Hoàn tiền là ghi nhận thủ công bên ngoài, không có refund/payout tự động. Phí giao hàng chưa tích hợp API hãng vận chuyển. Chưa nghiệm thu tải lớn/nhiều JVM, mobile thiết bị thật, upload video Cloudinary thật hoặc OTP email end-to-end trong đợt cuối. Lỗi cổng chưa rõ kết quả có thể giữ reservation; không suy HTTP 404 thành chưa nhận tiền. Voucher không trả lượt khi hủy. JWT grant/WebSocket hiện phục vụ một instance.

## Hồ sơ và việc nhóm xác nhận

File ở `outputs/final-2026-10-26/`: một báo cáo đồ án cuối kỳ, source ZIP, SQL và hướng dẫn chạy. Báo cáo có đủ họ tên/MSSV, phân công, 14 mẫu prompt và URL API của ba thành viên. Rubric online tự chấm 78/100 (7,8/10) mỗi người.

Nhóm cần quay video có mặt từng người và bổ sung minh chứng GitHub thực tế. Xác nhận payOS thay VNPAY/MOMO với giảng viên. Đã điền rubric tại [tab 27](https://docs.google.com/spreadsheets/d/13f45c1BgWIiv1SToJRQCnXyHbGX1lDLK/edit?gid=347682576#gid=347682576), tổng bằng công thức là 78/100 mỗi người. Báo cáo nằm trong [thư mục Nhóm 27](https://drive.google.com/drive/folders/1oXTum7aRQ1r3sFsoWTs29DQeNuyX4_V4); video cần bổ sung.

Lịch UTEx: **29/10/2026, 07:00–18:00, A5-203; nhóm 27 lượt 28**. Nộp trước lượt 30 phút; chưa có giờ riêng. Nguồn https://utexlms.hcmute.edu.vn/mod/page/view.php?id=1452010 . Rubric tải về cập nhật 24/10/2026.
