# Góc Sách

Đồ án Lập trình Web nhóm 27, đề tài 14: website chuỗi cửa hàng sách cũ.

| Thành viên | MSSV | Phụ trách |
|---|---|---|
| Lê Trọng Bảo | 22110106 | Checkout, đơn hàng, payOS, trả/hoàn tiền, thông báo |
| Nguyễn Kiều Thành | 22110231 | Tài khoản, catalog, hồ sơ/địa chỉ, giỏ, review/media |
| Nguyễn An Khang | 22110160 | Shop/kho, quản trị catalog, voucher/phí/doanh thu, kiểm duyệt |

## Chạy bằng Docker

Mở Docker Desktop, nhận riêng `team.properties` từ Bảo và đặt vào `config/`, rồi chạy:

```sh
docker compose up -d --build --wait
```

Website: http://localhost:8080. Hộp thư OTP: http://localhost:8025. Tài khoản `buyer` hoặc `admin`, mật khẩu `demo-password-123`. Dữ liệu và JWT tự tạo; payOS mặc định tắt. Xem [hướng dẫn Docker](docs/DOCKER_QUICKSTART.md).

## Tài liệu cần dùng

- [Báo cáo đồ án cuối kỳ và rubric](docs/reports/README.md).
- [Phân công và URL API](docs/API_REFERENCE.md).
- [Yêu cầu](docs/REQUIREMENTS.md) và [kiến trúc](docs/ARCHITECTURE.md).
- [Nghiệm thu và việc cần hoàn tất](docs/FINAL_ACCEPTANCE.md).
- [Kết quả kiểm thử](docs/VERIFICATION.md): lần chạy 28/10 đạt 230/230 test, cùng acceptance PostgreSQL/Compose. Không coi fake payment là thanh toán thật.
- [Chạy thủ công](docs/SETUP.md) nếu không dùng Docker.

Kiểm tra source bằng Java 21: `./mvnw.cmd -B -ntp verify`. Key thật không nằm trên Git. Các kế hoạch cũ và PDF trùng đã được bỏ; lịch sử vẫn có trong Git.
