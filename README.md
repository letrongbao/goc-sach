# Góc Sách

Website quản lý chuỗi cửa hàng sách cũ — định hướng đề tài 14.

## Hiện có

- Đăng ký, kích hoạt và quên mật khẩu bằng OTP email.
- Đăng nhập, ghi nhớ đăng nhập, phân quyền người dùng/quản trị.
- Quản lý danh mục, sách và ảnh bìa trên Cloudinary.
- Tủ sách tìm theo tên/tác giả, phân trang; giá và số lượng lấy từ kho từng cửa hàng.
- Chi tiết đầu sách có các bản đang sẵn bán, tình trạng và giá riêng.
- Đăng ký/duyệt cửa hàng, tạm khóa/mở lại và phân quyền thành viên theo cửa hàng (đang kiểm thử).
- Nhập từng bản sách cũ, phân loại, điều chỉnh giá, mở/ngừng bán và xem lịch sử kho.
- Hồ sơ, địa chỉ nhận hàng, yêu thích, đã xem và giỏ hàng theo từng bản sách.
- Checkout COD thử nghiệm: tách đơn theo shop, snapshot địa chỉ, giữ bản sách, chống gửi trùng và hủy đơn chờ xác nhận.

Nền tảng: Java 21, Spring Boot, Thymeleaf, Bootstrap, JPA, PostgreSQL và Cloudinary.

## Chạy thử

Xem [hướng dẫn chạy](docs/SETUP.md). Có thể dùng `scripts/preview.ps1` để xem giao diện bằng dữ liệu giả, không cần database và không gửi email.

Đồ án đang phát triển; bản xem thử dùng dữ liệu trong bộ nhớ, chưa thay cho nghiệm thu PostgreSQL. WebSocket và đánh giá đã có nền tảng; VNPAY, thông báo realtime, media đánh giá và vận hành nâng cao còn trong [kế hoạch phát triển](docs/ROADMAP.md). Xem [tiến độ từng mốc](docs/MILESTONES.md), không coi bản hiện tại là đồ án đã hoàn tất.

[Phạm vi](docs/FINAL_PROJECT.md) · [Kiểm thử](docs/VERIFICATION.md)
