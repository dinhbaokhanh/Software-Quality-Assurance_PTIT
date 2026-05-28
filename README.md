# Online Learning Platform

## 1. Chạy server để test trong IntelliJ

Mở thư mục [online-learning](online-learning) bằng IntelliJ IDEA, đảm bảo đang dùng JDK 21, rồi tạo một Run/Debug Configuration kiểu Spring Boot với main class là `com.ptit.onlinelearning.OnlineLearningApplication`.

Trong phần Environment variables, cấu hình tối thiểu các biến mà `application-dev.yml` đang dùng, đặc biệt là `DB_NAME`, `DB_PASSWORD`, `AWS_REGION_NAME`, `AWS_S3_BUCKET`, `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `CLOUDFRONT_HOST`, `VNPAY_URL`, `VNPAY_RETURN_URL`, `VNPAY_RETURN_URL_PRE_ORDER`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_API_URL`, `SENGRID_API_KEY`. Nếu cần test đầy đủ luồng backend, hãy chạy sẵn PostgreSQL, Redis và RabbitMQ theo các giá trị trong file cấu hình.

Thêm program arguments `--spring.profiles.active=dev`, sau đó bấm Run. Server sẽ khởi động trên port `8080` theo cấu hình hiện tại.

## 2. Unit test

Unit test trong dự án hiện tại chủ yếu là kiểm tra ngữ cảnh Spring có khởi động được hay không. Có thể chạy nhanh bằng Maven:

```shell
mvn test
```

Trong IntelliJ, bạn cũng có thể chạy trực tiếp class test `OnlineLearningApplicationTests`. Phần này chỉ nên dùng để kiểm tra logic nhỏ và không phụ thuộc nhiều vào môi trường ngoài.

## 3. Bỏ phần thừa

Các phần như tech stack, hướng dẫn clone, CI/integration test, troubleshooting, project structure và contributing đã được lược bỏ khỏi README vì không phục vụ mục tiêu unit test và khởi động server trong IntelliJ.
