FROM eclipse-temurin:17-jdk

WORKDIR /app

# copy toàn bộ source code
COPY . .

# fix lỗi Windows
RUN sed -i 's/\r$//' mvnw

# cấp quyền
RUN chmod +x mvnw

# build jar
RUN ./mvnw clean package -DskipTests

# chạy app
CMD ["sh", "-c", "java -jar target/*.jar"]