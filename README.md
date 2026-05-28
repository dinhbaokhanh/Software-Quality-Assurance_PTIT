# SQA Assignments 

## Thông tin chung

- **Giảng viên hướng dẫn:** Đỗ Thị Bích Ngọc
- **Thành viên nhóm:**
  - Đinh Bảo Khánh (B22DCVT281)
  - Lê Tiến Đạt
  - Trương Gia Huy
  - Phạm Thành Hùng

## Tài liệu

- [SQA Plan](https://docs.google.com/document/d/14SzqYvOzSAcVaDdzvud-SpP-ae8LHgBoX4YZF86uvWM/edit?usp=sharing)
- [System Test](https://docs.google.com/spreadsheets/d/1ut3jaWGeHhlKOk9BkE4KvtNY6Wykqodg2k4cBtnXll4/edit?usp=sharing)
- [SQA Review](https://docs.google.com/spreadsheets/d/1TH6qAIwB-VmL7R0Hp8YHST-PbS9FEJ5G5yOmyaBYnf8/edit?usp=sharing)
- [Unit Test](https://docs.google.com/spreadsheets/d/1416zVksrB3UFY4w7VMqRh3puT8Gl2xVSBAPaIYH0ATU/edit?usp=sharing)

## Overview

Phần unit test trong dự án này chủ yếu là các service test chạy trên Spring context thật, dùng `@SpringBootTest` và profile `test`. Các test không chỉ kiểm tra kết quả trả về mà còn kiểm tra dữ liệu trong database sau khi gọi service.

Hiện tại bộ test tập trung vào 4 nhóm chính: `CategoryService`, `EnrollmentService`, `LessonProgressService` và `QuizAttemptService`. Mỗi nhóm đều có các case cho nhánh đúng, nhánh lỗi, và một số case đặc biệt để phát hiện bug hiện có như thiếu validate null, kiểm tra sai quan hệ giữa dữ liệu, hoặc lỗi xử lý JPA lifecycle.

Vì vậy, dù đang được gọi là unit test, thực tế đây là lớp test ở mức service gần với integration test hơn. Mục tiêu của chúng là xác nhận logic nghiệp vụ và trạng thái dữ liệu sau khi chạy từng hàm quan trọng.

## 1. Chạy server để test trong IntelliJ

Mở thư mục [online-learning](online-learning) bằng IntelliJ IDEA, đảm bảo đang dùng JDK 21, rồi tạo một Run/Debug Configuration kiểu Spring Boot với main class là `com.ptit.onlinelearning.OnlineLearningApplication`.

Trong phần Environment variables, cấu hình tối thiểu các biến mà `application-dev.yml` đang dùng, đặc biệt là `DB_NAME`, `DB_PASSWORD`, `AWS_REGION_NAME`, `AWS_S3_BUCKET`, `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `CLOUDFRONT_HOST`, `VNPAY_URL`, `VNPAY_RETURN_URL`, `VNPAY_RETURN_URL_PRE_ORDER`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_API_URL`, `SENGRID_API_KEY`. Nếu cần test đầy đủ luồng backend, tự thiết lập env và chạy sẵn PostgreSQL, Redis và RabbitMQ theo các giá trị trong file cấu hình. Nếu cần xem Unit Test, đối chiếu với link unit test ở trên.

Thêm program arguments `--spring.profiles.active=dev`, sau đó bấm Run. Server sẽ khởi động trên port `8080` theo cấu hình hiện tại.

## 2. Unit test

Có thể chạy nhanh toàn bộ test bằng Maven:

```shell
mvn test
```

Trong IntelliJ, bạn cũng có thể chạy trực tiếp từng class test như `CategoryServiceUnitTest`, `EnrollmentServiceUnitTest`, `LessonProgressServiceUnitTest`, `QuizAttemptServiceUnitTest` hoặc `OnlineLearningApplicationTests`.

Các test này dùng dữ liệu thật trong database test, nên phù hợp nhất để kiểm tra logic nghiệp vụ và các nhánh xử lý quan trọng của service.
