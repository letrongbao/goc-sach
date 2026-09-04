# Góc Sách

Website quản lý chuỗi cửa hàng sách cũ — định hướng đề tài 14.

## Hiện có

- Đăng ký, kích hoạt và quên mật khẩu bằng OTP email.
- Đăng nhập, ghi nhớ đăng nhập, phân quyền người dùng/quản trị.
- Quản lý danh mục, sách và ảnh bìa.
- Tủ sách tìm theo tên/tác giả, phân trang; giá và số lượng lấy từ kho từng cửa hàng.
- Chi tiết đầu sách có các bản đang sẵn bán, tình trạng và giá riêng.
- Đăng ký/duyệt cửa hàng, tạm khóa/mở lại và phân quyền thành viên theo cửa hàng (đang kiểm thử).
- Nhập từng bản sách cũ, phân loại, điều chỉnh giá, mở/ngừng bán và xem lịch sử kho.

Nền tảng: Java 21, Spring Boot, Thymeleaf, Bootstrap, JPA và PostgreSQL.

## Chạy thử

Xem [hướng dẫn chạy](docs/SETUP.md). Có thể dùng `scripts/preview.ps1` để xem giao diện bằng dữ liệu giả, không cần database và không gửi email.

Đồ án đang phát triển; cửa hàng, kho và tủ sách mới được thử bằng dữ liệu trong bộ nhớ, chưa nghiệm thu PostgreSQL. Giỏ hàng, thanh toán, WebSocket và Cloudinary còn trong [kế hoạch phát triển](docs/ROADMAP.md). Xem [tiến độ từng mốc](docs/MILESTONES.md), không coi bản hiện tại là đồ án đã hoàn tất.

[Phạm vi](docs/FINAL_PROJECT.md) · [Kiểm thử](docs/VERIFICATION.md)
