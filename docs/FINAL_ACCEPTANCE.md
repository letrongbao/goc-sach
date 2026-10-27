# Nghiệm thu đồ án cuối kỳ Góc Sách

Chuẩn bị ngày 26/10/2026; kiểm tra JAR và xuất tài liệu hoàn tất ngày 27/10/2026. Chỉ phạm vi nhóm 27, đề tài 14. Tài liệu này cập nhật hiện trạng mới hơn các ghi chú mốc trước trong REQUIREMENTS, ARCHITECTURE và VERIFICATION.

## Chức năng bổ sung

- Bốn top 20 mới nhất, bán chạy, yêu thích, đánh giá; mỗi trang 10 đầu sách. Trang chủ có sách bán trên 10 bản.
- Lọc đơn theo trạng thái; chi tiết đơn hiển thị hãng giao, voucher và tiền giảm đã chốt.
- Voucher theo shop, bảng phí giao hàng nội bộ, snapshot chiết khấu và thống kê doanh thu.
- Trả hàng trong 7 ngày cho đơn đã giao và PAID; ghi nhận hoàn tiền bên ngoài có tham chiếu. Sách trả về ngừng bán để kiểm tra.
- Admin khóa user, tăng auth_version, ẩn/hiện review. Quyền kiểm tra ở service.
- Review ảnh/MP4 tối đa 5 MB qua Cloudinary; adapter video có test, chưa nghiệm thu upload thật.
- Native WebSocket `/ws` theo phiên, kiểm tra thay đổi mỗi 5 giây, chỉ phát trạng thái đơn được phép xem; thay cấu hình STOMP chưa dùng trước đây.

## Bằng chứng

`mvnw.cmd -B -ntp verify` hoàn tất 26/10/2026 17:56:26: **220 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Log local `target/final-verified.log`; XML `target/surefire-reports/`. Lần sandbox chặn socket không được dùng làm kết luận; lần hoàn chỉnh ngoài sandbox đạt.

`FinalDatabaseAcceptance` trên PostgreSQL demo: validate mapping, top, voucher/phí checkout, trả hàng, fake payOS PAID/callback lặp/hủy. Toàn bộ thao tác nghiệm thu rollback, dữ liệu seed giữ nguyên. Log `target/final-db-acceptance.log`.

Chrome preview đã kiểm tra trang chủ, đăng nhập, top bán chạy, lọc đơn, checkout voucher (315.000 + 40.000 - 10.000 = 345.000 đồng), quản trị và vận hành shop. Ảnh chụp thật nằm trong DOCX. FinalWebTest kiểm tra HTTP, quyền/CSRF và WebSocket cập nhật riêng.

Ngày 27/10: JAR production khởi động với JPA validate trên `goc_sach_final_demo`, port 18082; GET `/rankings/bestselling` trả HTTP 200. Cấu hình demo tắt payOS, SMTP trỏ localhost; Cloudinary dùng cấu hình sẵn có vì JAR bắt buộc bật adapter, không upload trong phép thử. `config/final-demo.properties` bị Git ignore và không nằm trong gói source.

Lỗi PostgreSQL scale BigDecimal 10000/10000.00 gây fingerprint khác nhau đã sửa bằng `stripTrailingZeros().toPlainString()` và thêm test hồi quy. Chi tiết đơn bổ sung breakdown giảm giá/hãng giao để đối chiếu tổng.

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

File ở `outputs/final-2026-10-26/`: báo cáo chung, ba báo cáo thành viên, rubric local, source zip và SQL. Hai bạn để trống tên/MSSV; phân công là đề xuất, prompt biên soạn lại có ghi rõ. Không giả lập lịch sử đóng góp.

Nhóm cần điền thông tin, xác nhận phân công/điểm, quay video có mặt từng người và bổ sung GitHub thật (mốc kiểm tra có 17 commit cùng tác giả). Xác nhận payOS thay VNPAY/MOMO với giảng viên. Không sửa hoặc nộp lên sheet/Drive công khai.

Lịch UTEx: **29/10/2026, 07:00–18:00, A5-203; nhóm 27 lượt 28**. Nộp trước lượt 30 phút; chưa có giờ riêng. Nguồn https://utexlms.hcmute.edu.vn/mod/page/view.php?id=1452010 . Rubric tải về cập nhật 24/10/2026.
