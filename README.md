# Góc Sách

Website chuỗi cửa hàng sách cũ, phát triển theo đề tài 14.

## Chức năng hiện có

- Đăng ký, mã xác nhận email, đăng nhập và khôi phục mật khẩu.
- Tìm và lọc sách theo danh mục, giá, tình trạng, cửa hàng; xem trang cửa hàng và từng cuốn đang bán.
- Hồ sơ, nhiều địa chỉ, yêu thích, sách đã xem và giỏ hàng lưu theo tài khoản.
- Đặt hàng thanh toán khi nhận; tách đơn theo cửa hàng, chốt giá và thông tin nhận hàng tại thời điểm mua.
- Người mua theo dõi hoặc hủy đơn đang chờ. Người bán xác nhận, chuẩn bị, giao, hoàn tất hoặc hủy đơn đúng quy trình.
- Quản lý cửa hàng, danh mục, đầu sách, từng cuốn sách cũ và ảnh.

## Chạy dự án

Xem [hướng dẫn chạy](docs/SETUP.md). Dùng `scripts/preview.ps1` nếu chỉ muốn xem giao diện với dữ liệu minh họa, không cần database và không gửi email.

Lệnh kiểm tra toàn bộ:

```powershell
.\mvnw.cmd -B -ntp verify
```

Kết quả gần nhất: **136/136 test đạt** và đóng gói JAR thành công. SQL chỉ được chuẩn bị để xem xét, dự án không tự tạo hoặc cập nhật bảng.

Các phần tiếp theo gồm thanh toán VNPAY, khuyến mãi, đánh giá có ảnh/video, thông báo đơn hàng và báo cáo vận hành. Xem [tiến độ](docs/MILESTONES.md), [kết quả kiểm tra](docs/VERIFICATION.md) và [kế hoạch](docs/ROADMAP.md).
