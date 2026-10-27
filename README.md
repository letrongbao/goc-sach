# Góc Sách

Website chuỗi cửa hàng sách cũ, phát triển theo đề tài 14.

Bản cuối kỳ cập nhật 28/10/2026: xem [nghiệm thu và hồ sơ](docs/FINAL_ACCEPTANCE.md). Đã bổ sung top 20, vận hành, review media và WebSocket; bộ verify cuối đạt 230 test. Các giới hạn dịch vụ thật được ghi riêng.

API và phân công ba người: [URL, body, quyền và cách lấy token](docs/API_REFERENCE.md). Báo cáo Word/PDF và rubric local: [hồ sơ](docs/reports/README.md).

## Chức năng hiện có

- Đăng ký, mã xác nhận email, đăng nhập và khôi phục mật khẩu.
- Tìm và lọc sách theo danh mục, giá, tình trạng, cửa hàng; xem trang cửa hàng và từng cuốn đang bán.
- Trang chủ khám phá theo danh mục/ngân sách, sách được yêu thích, cẩm nang mua sách cũ; sắp xếp mới nhất, giá và lượt yêu thích.
- Hồ sơ, nhiều địa chỉ, yêu thích, sách đã xem và giỏ hàng lưu theo tài khoản.
- Đặt hàng thanh toán khi nhận; tách đơn theo cửa hàng, chốt giá và thông tin nhận hàng tại thời điểm mua.
- Chuyển khoản qua PayOS; theo dõi thanh toán, hủy khi chưa nhận tiền và kiểm tra các khoản cần đối chiếu.
- Người mua theo dõi hoặc hủy đơn đang chờ. Người bán xác nhận, chuẩn bị, giao, hoàn tất hoặc hủy đơn đúng quy trình.
- Người đã nhận hàng viết/sửa đánh giá 1–5 sao; xem cảm nhận, điểm trung bình và phân trang trên trang sách.
- Quản lý cửa hàng, danh mục, đầu sách, từng cuốn sách cũ và ảnh.

## Chạy dự án

Xem [hướng dẫn chạy](docs/SETUP.md). Dùng `scripts/preview.ps1` nếu chỉ muốn xem giao diện với dữ liệu minh họa, không cần database và không gửi email.

Lệnh kiểm tra toàn bộ:

```powershell
.\mvnw.cmd -B -ntp verify
```

Kết quả kiểm thử được cập nhật ở [VERIFICATION.md](docs/VERIFICATION.md). SQL chỉ được chuẩn bị để xem xét, dự án không tự tạo hoặc cập nhật bảng.

Đã có khuyến mãi, đánh giá có ảnh/video, thông báo đơn hàng và báo cáo vận hành. PayOS là lựa chọn hiện tại; cần xác nhận với thầy vì yêu cầu ghi VNPAY/MOMO. Xem [tiến độ](docs/MILESTONES.md) và [kế hoạch](docs/ROADMAP.md).

Thiết kế, hành vi mới và tài khoản demo được ghi tại [UI/UX](docs/UI_UX.md).
